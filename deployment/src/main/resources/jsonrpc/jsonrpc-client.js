/**
 * Quarkus JSON-RPC 2.0 WebSocket Client
 *
 * Provides a Promise-based API for calling JSON-RPC methods and subscribing
 * to streaming responses over WebSocket.
 */

export class JsonRPCClient {
    static _config = {};

    /**
     * Set global defaults for all JsonRPCClient instances.
     *
     * @param {Object} options - Configuration options
     * @param {string} [options.token] - Static bearer token
     * @param {Function} [options.tokenProvider] - Callback returning a token string
     */
    static configure(options = {}) {
        JsonRPCClient._config = { ...JsonRPCClient._config, ...options };
    }

    _ws = null;
    _url = null;
    _path;
    _nextId = 0;
    _pending = new Map();
    _subscriptions = new Map();
    _listeners = new Map();
    _autoReconnect;
    _reconnectDelay;
    _maxReconnectDelay;
    _reconnectTimer = null;
    _manuallyDisconnected = false;
    _connected = false;
    _token = null;
    _tokenProvider = null;

    onOpen = null;
    onClose = null;
    onError = null;

    constructor(options = {}) {
        const merged = { ...JsonRPCClient._config, ...options };
        this._path = merged.path || '/quarkus/json-rpc';
        this._url = merged.url || null;
        this._autoReconnect = merged.autoReconnect !== false;
        this._reconnectDelay = 1000;
        this._maxReconnectDelay = merged.maxReconnectDelay || 30000;
        this._token = merged.token || null;
        this._tokenProvider = merged.tokenProvider || null;

        if (merged.onOpen) this.onOpen = merged.onOpen;
        if (merged.onClose) this.onClose = merged.onClose;
        if (merged.onError) this.onError = merged.onError;

        if (merged.autoConnect !== false) {
            this.connect();
        }
    }

    get url() {
        if (this._url) return this._url;
        const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
        return `${protocol}//${location.host}${this._path}`;
    }

    set url(value) {
        const changed = this._url !== value;
        this._url = value;
        if (changed && this._ws) {
            this.disconnect();
            this.connect();
        }
    }

    get path() {
        return this._path;
    }

    set path(value) {
        const changed = this._path !== value;
        this._path = value;
        if (changed && this._ws && !this._url) {
            this.disconnect();
            this.connect();
        }
    }

    get connected() {
        return this._connected;
    }

    get token() {
        return this._token;
    }

    set token(value) {
        this._token = value;
    }

    /**
     * Update the authentication token and reconnect.
     *
     * @param {string} newToken - The new bearer token
     */
    updateToken(newToken) {
        this._token = newToken;
        if (this._ws) {
            this.disconnect();
            this._manuallyDisconnected = false;
            this.connect();
        }
    }

    connect() {
        this._manuallyDisconnected = false;
        if (this._ws) return;
        if (this._reconnectTimer) {
            clearTimeout(this._reconnectTimer);
            this._reconnectTimer = null;
        }

        const protocols = this._buildProtocols();
        const ws = protocols.length > 0
            ? new WebSocket(this.url, protocols)
            : new WebSocket(this.url);
        this._ws = ws;

        ws.onopen = () => {
            this._connected = true;
            this._reconnectDelay = 1000;
            if (this.onOpen) this.onOpen();
        };

        ws.onclose = (event) => {
            this._connected = false;
            this._ws = null;

            for (const [, pending] of this._pending) {
                pending.reject(new Error('WebSocket closed'));
            }
            this._pending.clear();

            for (const [, sub] of this._subscriptions) {
                sub._push('error', new Error('WebSocket closed'));
            }
            this._subscriptions.clear();

            if (this.onClose) this.onClose(event);

            if (this._autoReconnect && !this._manuallyDisconnected) {
                const jitter = Math.random() * 1000;
                this._reconnectTimer = setTimeout(() => {
                    this._reconnectTimer = null;
                    this.connect();
                }, this._reconnectDelay + jitter);
                this._reconnectDelay = Math.min(
                    this._reconnectDelay * 2,
                    this._maxReconnectDelay
                );
            }
        };

        ws.onerror = (error) => {
            if (this.onError) this.onError(error);
        };

        ws.onmessage = (event) => {
            try {
                this._handleMessage(JSON.parse(event.data));
            } catch (e) {
                if (this.onError) this.onError(e);
            }
        };
    }

    disconnect() {
        this._manuallyDisconnected = true;
        if (this._reconnectTimer) {
            clearTimeout(this._reconnectTimer);
            this._reconnectTimer = null;
        }
        if (this._ws) {
            this._ws.close();
            this._ws = null;
        }
    }

    /**
     * Call a JSON-RPC method and return a Promise for the result.
     *
     * @param {string} method - Method key, e.g. "HelloResource#hello"
     * @param {Object|Array} [params] - Named params object or positional params array
     * @returns {Promise<*>} Resolves with the result value, rejects with error
     */
    call(method, params) {
        return new Promise((resolve, reject) => {
            const id = ++this._nextId;
            this._pending.set(id, { resolve, reject });
            const msg = { jsonrpc: '2.0', id, method };
            if (params != null) msg.params = params;
            this._send(msg);
        });
    }

    /**
     * Subscribe to a streaming JSON-RPC method (Multi / Flow.Publisher).
     * Returns a Subscription object for receiving items.
     *
     * @param {string} method - Method key, e.g. "PojoResource#pojoMulti"
     * @param {Object|Array} [params] - Named params object or positional params array
     * @returns {Subscription}
     */
    subscribe(method, params) {
        const sub = new Subscription(this);
        const id = ++this._nextId;
        this._pending.set(id, {
            resolve: (result) => {
                sub._id = result;
                this._subscriptions.set(result, sub);
            },
            reject: (error) => {
                sub._push('error', error);
            }
        });
        const msg = { jsonrpc: '2.0', id, method };
        if (params != null) msg.params = params;
        this._send(msg);
        return sub;
    }

    /**
     * Listen for server push notifications (from JsonRPCBroadcaster).
     *
     * @param {string} method - Notification method name
     * @param {Function} callback - Called with the notification data
     * @returns {Function} Unsubscribe function
     */
    on(method, callback) {
        if (!this._listeners.has(method)) {
            this._listeners.set(method, []);
        }
        this._listeners.get(method).push(callback);
        return () => {
            const cbs = this._listeners.get(method);
            if (cbs) {
                const idx = cbs.indexOf(callback);
                if (idx >= 0) cbs.splice(idx, 1);
            }
        };
    }

    /**
     * Cancel a streaming subscription by its ID.
     *
     * @param {string} subscriptionId - UUID returned by the subscription ACK
     * @returns {Promise<boolean>}
     */
    unsubscribe(subscriptionId) {
        return this.call('unsubscribe', { subscription: subscriptionId }).then(result => {
            this._subscriptions.delete(subscriptionId);
            return result;
        });
    }

    _resolveToken() {
        if (this._tokenProvider) {
            return this._tokenProvider();
        }
        return this._token;
    }

    _buildProtocols() {
        const token = this._resolveToken();
        if (!token) return [];
        const encoded = encodeURIComponent(
            'quarkus-http-upgrade#Authorization#' + token
        );
        return ['bearer-token-carrier', encoded];
    }

    _send(message) {
        if (this._ws && this._ws.readyState === WebSocket.OPEN) {
            this._ws.send(JSON.stringify(message));
        } else if (message.id !== undefined) {
            const pending = this._pending.get(message.id);
            if (pending) {
                this._pending.delete(message.id);
                pending.reject(new Error('WebSocket not connected'));
            }
        }
    }

    _handleMessage(data) {
        // Subscription notification
        if (data.method === 'subscription' && data.params) {
            const subId = data.params.subscription;
            const sub = this._subscriptions.get(subId);
            if (sub) {
                if (data.params.error) {
                    sub._push('error', data.params.error);
                    this._subscriptions.delete(subId);
                } else if (data.params.complete) {
                    sub._push('complete');
                    this._subscriptions.delete(subId);
                } else if (data.params.result !== undefined) {
                    sub._push('item', data.params.result);
                }
            }
            return;
        }

        // Server push notification (broadcast)
        if (data.method && data.id === undefined) {
            const listeners = this._listeners.get(data.method);
            if (listeners) {
                const result = data.params && data.params.result !== undefined
                    ? data.params.result
                    : data.params;
                listeners.forEach(cb => cb(result));
            }
            return;
        }

        // Regular response
        if (data.id !== undefined) {
            const pending = this._pending.get(data.id);
            if (pending) {
                this._pending.delete(data.id);
                if (data.error) {
                    pending.reject(data.error);
                } else {
                    pending.resolve(data.result);
                }
            }
        }
    }
}

/**
 * Represents an active streaming subscription.
 * Register callbacks with onItem/onError/onComplete, cancel with cancel().
 */
export class Subscription {
    _id = null;
    _onItemCb = null;
    _onErrorCb = null;
    _onCompleteCb = null;
    _client;
    _buffer = [];

    constructor(client) {
        this._client = client;
    }

    get id() {
        return this._id;
    }

    onItem(callback) {
        this._onItemCb = callback;
        this._drain();
        return this;
    }

    onError(callback) {
        this._onErrorCb = callback;
        this._drain();
        return this;
    }

    onComplete(callback) {
        this._onCompleteCb = callback;
        this._drain();
        return this;
    }

    _push(type, value) {
        if (type === 'item' && this._onItemCb) {
            this._onItemCb(value);
        } else if (type === 'error' && this._onErrorCb) {
            this._onErrorCb(value);
        } else if (type === 'complete' && this._onCompleteCb) {
            this._onCompleteCb();
        } else {
            this._buffer.push({ type, value });
        }
    }

    _drain() {
        const remaining = [];
        for (const entry of this._buffer) {
            if (entry.type === 'item' && this._onItemCb) {
                this._onItemCb(entry.value);
            } else if (entry.type === 'error' && this._onErrorCb) {
                this._onErrorCb(entry.value);
            } else if (entry.type === 'complete' && this._onCompleteCb) {
                this._onCompleteCb();
            } else {
                remaining.push(entry);
            }
        }
        this._buffer = remaining;
    }

    cancel() {
        if (this._id) {
            return this._client.unsubscribe(this._id);
        }
        return Promise.resolve(false);
    }
}
