import { QwcHotReloadElement, html, css } from 'qwc-hot-reload-element';
import { methods } from 'build-time-data';
import { endpointPath } from 'build-time-data';
import { JsonRpc } from 'jsonrpc';

export class QwcJsonRpcTester extends QwcHotReloadElement {

    static _nextRequestId = 0;
    static _connections = new Map();

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: 1em;
            padding: 1em;
        }
        .form-row {
            display: flex;
            align-items: center;
            gap: 0.5em;
        }
        .form-row label {
            flex: 0 0 120px;
            font-weight: bold;
        }
        .result-area {
            background: var(--lumo-contrast-5pct);
            border-radius: var(--lumo-border-radius-m);
            padding: 1em;
            font-family: monospace;
            white-space: pre-wrap;
            word-break: break-all;
            min-height: 80px;
            max-height: 400px;
            overflow: auto;
        }
        .stream-items {
            display: flex;
            flex-direction: column;
            gap: 0.25em;
        }
        .stream-item {
            padding: 0.25em 0.5em;
            background: var(--lumo-contrast-5pct);
            border-radius: var(--lumo-border-radius-s);
            font-family: monospace;
            font-size: 0.9em;
        }
        select, input, button, textarea {
            padding: 0.4em 0.6em;
            border-radius: var(--lumo-border-radius-s);
            border: 1px solid var(--lumo-contrast-20pct);
            font-size: 0.9em;
        }
        select {
            min-width: 300px;
        }
        input {
            min-width: 250px;
        }
        textarea {
            min-width: 350px;
            min-height: 60px;
            font-family: 'JetBrains Mono', 'Fira Code', monospace;
            font-size: 0.85em;
            resize: vertical;
        }
        button {
            cursor: pointer;
            background: var(--lumo-primary-color);
            color: var(--lumo-primary-contrast-color);
            border: none;
            padding: 0.5em 1.2em;
            font-weight: 500;
        }
        button:hover {
            opacity: 0.9;
        }
        button.secondary {
            background: var(--lumo-contrast-20pct);
            color: var(--lumo-body-text-color);
        }
        .endpoint-info {
            font-size: 0.85em;
            color: var(--lumo-secondary-text-color);
        }
        .security-info {
            display: inline-block;
            padding: 4px 12px;
            border-radius: 12px;
            font-size: 0.85em;
            font-weight: 500;
            background: var(--lumo-primary-color-10pct, #e3f2fd);
            color: var(--lumo-primary-text-color, #1565c0);
        }
    `;

    static properties = {
        _methods: { state: true },
        _selectedMethod: { state: true },
        _paramValues: { state: true },
        _result: { state: true },
        _streaming: { state: true },
        _streamItems: { state: true },
    };

    constructor() {
        super();
        this._methods = methods;
        this._selectedMethod = null;
        this._paramValues = {};
        this._result = null;
        this._streaming = false;
        this._streamItems = [];
        this._subscriptionId = null;
        this._subscriptionPath = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this._loadMethods();
    }

    _loadMethods(preselectedKey) {
        this.jsonRpc.listMethods().then(r => {
            this._methods = r.result || [];
            if (preselectedKey) {
                const found = this._methods.find(m => m.key === preselectedKey);
                if (found) {
                    this._selectedMethod = found;
                    return;
                }
            }
            if (!this._selectedMethod || !this._methods.find(m => m.key === this._selectedMethod.key)) {
                this._selectedMethod = this._methods.length > 0 ? this._methods[0] : null;
            }
        });
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        this._cancelStream();
    }

    render() {
        const preselected = sessionStorage.getItem('jsonrpc-test-method');
        if (preselected) {
            sessionStorage.removeItem('jsonrpc-test-method');
            this._loadMethods(preselected);
        }
        return html`
            <span class="endpoint-info">Endpoint: <code>${this._selectedMethod?.path || endpointPath}</code></span>

            <div class="form-row">
                <label>Method:</label>
                <select @change=${this._onMethodSelect}>
                    ${this._methods.map(m => html`
                        <option value=${m.key} ?selected=${this._selectedMethod?.key === m.key}>
                            ${m.key}
                        </option>
                    `)}
                </select>
            </div>

            ${this._selectedMethod?.security ? html`
                <div class="form-row">
                    <label>Security:</label>
                    <span class="security-info">${this._selectedMethod.security}</span>
                </div>
            ` : ''}

            ${this._selectedMethod?.parameters?.length > 0 ?
                this._selectedMethod.parameters.split(', ').map(p => {
                    const [name] = p.split(':').map(s => s.trim());
                    const type = p.split(':')[1]?.trim() || '';
                    const isComplex = this._isComplexType(type);
                    return html`
                        <div class="form-row">
                            <label>${name} <small>(${type})</small>:</label>
                            ${isComplex ? html`
                                <textarea
                                    .value=${this._paramValues[name] || ''}
                                    @input=${e => this._onParamInput(name, e.target.value)}
                                    placeholder='{"field": "value"}'></textarea>
                            ` : html`
                                <input type="text"
                                    .value=${this._paramValues[name] || ''}
                                    @input=${e => this._onParamInput(name, e.target.value)}
                                    placeholder="${name}">
                            `}
                        </div>
                    `;
                })
            : ''}

            <div class="form-row">
                <button @click=${this._invoke}>Invoke</button>
                ${this._streaming ? html`
                    <button class="secondary" @click=${this._cancelStream}>Cancel Stream</button>
                ` : ''}
            </div>

            ${this._streamItems.length > 0 ? html`
                <div>
                    <strong>Stream items (${this._streamItems.length}):</strong>
                    <div class="stream-items">
                        ${this._streamItems.map(item => html`
                            <div class="stream-item">${JSON.stringify(item)}</div>
                        `)}
                    </div>
                </div>
            ` : ''}

            ${this._result !== null ? html`
                <div>
                    <strong>Result:</strong>
                    <div class="result-area">${typeof this._result === 'object'
                        ? JSON.stringify(this._result, null, 2)
                        : String(this._result)}</div>
                </div>
            ` : ''}
        `;
    }

    hotReload() {
        this._cancelStream();
        QwcJsonRpcTester._connections.forEach(conn => conn.ws.close());
        QwcJsonRpcTester._connections.clear();
        this._paramValues = {};
        this._result = null;
        this._streamItems = [];
        this._loadMethods();
    }

    _onMethodSelect(e) {
        const key = e.target.value;
        this._selectedMethod = this._methods.find(m => m.key === key);
        this._paramValues = {};
        this._result = null;
        this._streamItems = [];
        this._cancelStream();
    }

    _onParamInput(name, value) {
        this._paramValues = { ...this._paramValues, [name]: value };
    }

    _isComplexType(type) {
        const simple = ['String', 'int', 'long', 'double', 'float', 'boolean',
            'short', 'byte', 'char', 'Integer', 'Long', 'Double', 'Float',
            'Boolean', 'Short', 'Byte', 'Character', 'BigDecimal', 'BigInteger',
            'List', 'Set', 'Map', 'Optional'];
        return type && !simple.includes(type);
    }

    _parseLenientJson(str) {
        try {
            return JSON.parse(str);
        } catch (e) {
            // Try fixing unquoted keys: {name: "val"} -> {"name": "val"}
            try {
                const fixed = str.replace(/([{,]\s*)([a-zA-Z_]\w*)\s*:/g, '$1"$2":');
                return JSON.parse(fixed);
            } catch (e2) {
                return str;
            }
        }
    }

    _invoke() {
        this._result = null;
        this._streamItems = [];
        this._cancelStream();

        const params = {};
        if (this._selectedMethod?.parameters) {
            this._selectedMethod.parameters.split(', ').forEach(p => {
                const name = p.split(':')[0].trim();
                const type = p.split(':')[1]?.trim() || '';
                let val = this._paramValues[name] || '';
                if (this._isComplexType(type)) {
                    val = this._parseLenientJson(val);
                } else {
                    try {
                        val = JSON.parse(val);
                    } catch (e) {
                        // keep as string
                    }
                }
                params[name] = val;
            });
        }

        this._invokeViaWebSocket(params);
    }

    static _getConnection(path) {
        const loc = window.location;
        const wsProtocol = loc.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${wsProtocol}//${loc.host}${path}`;

        let conn = QwcJsonRpcTester._connections.get(path);
        if (conn && conn.ws.readyState === WebSocket.OPEN) {
            return Promise.resolve(conn);
        }

        if (conn && conn.ws.readyState === WebSocket.CONNECTING) {
            return conn.ready;
        }

        // Clean up old connection if it exists
        if (conn) {
            conn.ws.close();
        }

        const ws = new WebSocket(wsUrl);
        conn = {
            ws,
            pending: new Map(),
            subscriptions: new Map(),
            ready: null,
        };

        conn.ready = new Promise((resolve, reject) => {
            ws.onopen = () => resolve(conn);
            ws.onerror = () => reject(new Error('WebSocket connection error'));
        });

        ws.onmessage = (event) => {
            const data = JSON.parse(event.data);

            // Subscription notification
            if (data.method === 'subscription' && data.params) {
                const subId = data.params.subscription;
                const sub = conn.subscriptions.get(subId);
                if (sub) {
                    if (data.params.result !== undefined) {
                        sub.onItem(data.params.result);
                    }
                    if (data.params.complete) {
                        sub.onComplete();
                        conn.subscriptions.delete(subId);
                    }
                    if (data.params.error) {
                        sub.onError(data.params.error);
                        conn.subscriptions.delete(subId);
                    }
                }
                return;
            }

            // Regular response
            if (data.id !== undefined) {
                const handler = conn.pending.get(data.id);
                if (handler) {
                    conn.pending.delete(data.id);
                    handler(data);
                }
            }
        };

        ws.onclose = () => {
            QwcJsonRpcTester._connections.delete(path);
            conn.pending.forEach(handler => handler({
                error: { code: -1, message: 'WebSocket closed' }
            }));
            conn.pending.clear();
            conn.subscriptions.forEach(sub => sub.onError('WebSocket closed'));
            conn.subscriptions.clear();
        };

        QwcJsonRpcTester._connections.set(path, conn);
        return conn.ready;
    }

    _invokeViaWebSocket(params) {
        const methodPath = this._selectedMethod?.path || endpointPath;
        const method = this._selectedMethod.key.replace(/\(.*\)$/, '');
        const requestId = ++QwcJsonRpcTester._nextRequestId;

        QwcJsonRpcTester._getConnection(methodPath).then(conn => {
            const request = {
                jsonrpc: '2.0',
                id: requestId,
                method: method,
            };
            if (Object.keys(params).length > 0) {
                request.params = params;
            }

            conn.pending.set(requestId, (data) => {
                if (data.result !== undefined) {
                    if (typeof data.result === 'string' &&
                        /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(data.result)) {
                        this._streaming = true;
                        this._subscriptionId = data.result;
                        this._subscriptionPath = methodPath;
                        this._result = `Subscription started: ${data.result}`;
                        conn.subscriptions.set(data.result, {
                            onItem: (item) => {
                                this._streamItems = [...this._streamItems, item];
                            },
                            onComplete: () => {
                                this._streaming = false;
                                this._result = 'Stream completed';
                                conn.subscriptions.delete(this._subscriptionId);
                                this._subscriptionId = null;
                                this._subscriptionPath = null;
                            },
                            onError: (err) => {
                                this._streaming = false;
                                this._result = `Stream error: ${err}`;
                                conn.subscriptions.delete(this._subscriptionId);
                                this._subscriptionId = null;
                                this._subscriptionPath = null;
                            },
                        });
                        return;
                    }
                    this._result = data.result;
                } else if (data.error) {
                    this._result = `Error: ${data.error.message} (code: ${data.error.code})`;
                }
            });

            conn.ws.send(JSON.stringify(request));
        }).catch(err => {
            this._result = `Connection error: ${err.message}`;
        });
    }

    _cancelStream() {
        if (this._subscriptionId && this._subscriptionPath) {
            const conn = QwcJsonRpcTester._connections.get(this._subscriptionPath);
            if (conn && conn.ws.readyState === WebSocket.OPEN) {
                const request = {
                    jsonrpc: '2.0',
                    id: ++QwcJsonRpcTester._nextRequestId,
                    method: 'unsubscribe',
                    params: [this._subscriptionId],
                };
                conn.ws.send(JSON.stringify(request));
                conn.subscriptions.delete(this._subscriptionId);
            }
        }
        this._streaming = false;
        this._subscriptionId = null;
        this._subscriptionPath = null;
    }
}

customElements.define('qwc-json-rpc-tester', QwcJsonRpcTester);
