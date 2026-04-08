import {LitElement, html, css} from 'lit';

class JsonRpcApp extends LitElement {

    static properties = {
        _ws: {state: true},
        _connected: {state: true},
        _method: {state: true},
        _params: {state: true},
        _messages: {state: true},
        _nextId: {state: true},
        _subscriptions: {state: true},
    };

    static styles = css`
        :host {
            display: block;
            font-family: system-ui, -apple-system, sans-serif;
            max-width: 960px;
            margin: 0 auto;
            padding: 24px;
            color: #1a1a2e;
        }

        h1 {
            font-size: 1.6rem;
            margin: 0 0 24px 0;
            color: #0d47a1;
        }

        .layout {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 20px;
        }

        .panel {
            background: #fff;
            border: 1px solid #e0e0e0;
            border-radius: 8px;
            padding: 16px;
        }

        .panel h2 {
            font-size: 1rem;
            margin: 0 0 12px 0;
            color: #333;
        }

        .status {
            display: inline-block;
            padding: 3px 10px;
            border-radius: 12px;
            font-size: 0.8rem;
            font-weight: 600;
            margin-left: 8px;
        }

        .status.connected {
            background: #e8f5e9;
            color: #2e7d32;
        }

        .status.disconnected {
            background: #ffebee;
            color: #c62828;
        }

        label {
            display: block;
            font-size: 0.85rem;
            font-weight: 500;
            margin-bottom: 4px;
            color: #555;
        }

        select, input, textarea {
            width: 100%;
            padding: 8px;
            border: 1px solid #ccc;
            border-radius: 4px;
            font-family: inherit;
            font-size: 0.9rem;
            box-sizing: border-box;
            margin-bottom: 12px;
        }

        textarea {
            font-family: 'JetBrains Mono', 'Fira Code', monospace;
            font-size: 0.8rem;
            resize: vertical;
        }

        .quick-methods {
            display: flex;
            flex-wrap: wrap;
            gap: 6px;
            margin-bottom: 12px;
        }

        .quick-methods button {
            padding: 4px 10px;
            font-size: 0.75rem;
            background: #e3f2fd;
            border: 1px solid #90caf9;
            border-radius: 4px;
            cursor: pointer;
            color: #1565c0;
        }

        .quick-methods button:hover {
            background: #bbdefb;
        }

        .actions {
            display: flex;
            gap: 8px;
        }

        .actions button {
            padding: 8px 20px;
            border: none;
            border-radius: 4px;
            font-size: 0.9rem;
            font-weight: 500;
            cursor: pointer;
        }

        .btn-send {
            background: #1976d2;
            color: white;
        }

        .btn-send:hover {
            background: #1565c0;
        }

        .btn-send:disabled {
            background: #bbb;
            cursor: not-allowed;
        }

        .btn-clear {
            background: #f5f5f5;
            border: 1px solid #ccc;
            color: #555;
        }

        .btn-clear:hover {
            background: #eee;
        }

        .messages {
            height: 460px;
            overflow-y: auto;
            font-family: 'JetBrains Mono', 'Fira Code', monospace;
            font-size: 0.78rem;
            line-height: 1.5;
        }

        .msg {
            padding: 8px;
            border-bottom: 1px solid #f0f0f0;
        }

        .msg.sent {
            background: #e3f2fd;
        }

        .msg.received {
            background: #fff;
        }

        .msg.error {
            background: #ffebee;
        }

        .msg.notification {
            background: #f3e5f5;
        }

        .msg-label {
            font-weight: 600;
            font-size: 0.7rem;
            text-transform: uppercase;
            margin-bottom: 4px;
        }

        .msg-label.sent { color: #1565c0; }
        .msg-label.received { color: #2e7d32; }
        .msg-label.error { color: #c62828; }
        .msg-label.notification { color: #7b1fa2; }

        .json-block {
            white-space: pre;
            word-break: normal;
            overflow-x: auto;
            line-height: 1.4;
        }

        .json-key { color: #881391; }
        .json-string { color: #0b7c0b; }
        .json-number { color: #1565c0; }
        .json-bool { color: #d32f2f; }
        .json-null { color: #9e9e9e; font-style: italic; }
        .json-brace { color: #555; }

        .msg.sent .json-key { color: #0d47a1; }
        .msg.sent .json-string { color: #1b5e20; }

        .msg.error .json-key { color: #880e4f; }
        .msg.error .json-string { color: #b71c1c; }

        .subscriptions {
            margin-top: 12px;
        }

        .sub-item {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 6px 8px;
            background: #f3e5f5;
            border-radius: 4px;
            margin-bottom: 4px;
            font-size: 0.8rem;
        }

        .sub-item button {
            padding: 2px 8px;
            font-size: 0.75rem;
            background: #e91e63;
            color: white;
            border: none;
            border-radius: 3px;
            cursor: pointer;
        }
    `;

    constructor() {
        super();
        this._connected = false;
        this._method = 'HelloResource#hello';
        this._params = '';
        this._messages = [];
        this._nextId = 1;
        this._subscriptions = new Map();
    }

    connectedCallback() {
        super.connectedCallback();
        this._connect();
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        if (this._ws) this._ws.close();
    }

    _connect() {
        const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const ws = new WebSocket(`${protocol}//${location.host}/quarkus/json-rpc`);

        ws.onopen = () => {
            this._connected = true;
            this._ws = ws;
        };

        ws.onclose = () => {
            this._connected = false;
            this._ws = null;
            this._subscriptions = new Map();
            setTimeout(() => this._connect(), 2000);
        };

        ws.onmessage = (event) => {
            const data = JSON.parse(event.data);
            if (data.method === 'subscription') {
                this._addMessage('notification', data);
            } else if (data.error && data.error.message) {
                this._addMessage('error', data);
            } else {
                this._addMessage('received', data);
            }
        };
    }

    _addMessage(type, data) {
        this._messages = [...this._messages, {type, data, time: new Date().toLocaleTimeString()}];
        this.updateComplete.then(() => {
            const container = this.shadowRoot.querySelector('.messages');
            if (container) container.scrollTop = container.scrollHeight;
        });
    }

    _send() {
        if (!this._ws) return;

        const id = this._nextId++;
        const req = {jsonrpc: '2.0', method: this._method, id};

        const paramsText = this._params.trim();
        if (paramsText) {
            try {
                req.params = JSON.parse(paramsText);
            } catch {
                this._addMessage('error', {error: {message: 'Invalid JSON in params field'}});
                return;
            }
        }

        this._addMessage('sent', req);
        this._ws.send(JSON.stringify(req));

        // Track subscription acks
        const origOnMessage = this._ws.onmessage;
        const handler = (event) => {
            const data = JSON.parse(event.data);
            if (data.id === id && data.result && typeof data.result === 'string'
                && data.result.match(/^[0-9a-f]{8}-/)) {
                this._subscriptions = new Map([...this._subscriptions, [data.result, this._method]]);
            }
        };
        this._ws.addEventListener('message', handler, {once: true});
    }

    _unsubscribe(subscriptionId) {
        if (!this._ws) return;
        const id = this._nextId++;
        const req = {jsonrpc: '2.0', method: 'unsubscribe', params: {subscription: subscriptionId}, id};
        this._addMessage('sent', req);
        this._ws.send(JSON.stringify(req));
        const subs = new Map(this._subscriptions);
        subs.delete(subscriptionId);
        this._subscriptions = subs;
    }

    _selectMethod(method, params) {
        this._method = method;
        this._params = params || '';
    }

    _clearMessages() {
        this._messages = [];
    }

    _renderJson(obj) {
        const jsonStr = JSON.stringify(obj, null, 2);
        const parts = [];
        const re = /("(?:\\.|[^"\\])*")\s*:/g;
        let last = 0;
        let match;

        // First pass: collect key positions
        const keys = [];
        while ((match = re.exec(jsonStr)) !== null) {
            keys.push({start: match.index, end: match.index + match[1].length, key: match[1]});
        }

        // Build highlighted spans character by character using a simpler token approach
        const highlighted = this._highlightJson(jsonStr);
        return html`<div class="json-block">${highlighted}</div>`;
    }

    _highlightJson(str) {
        const tokens = [];
        let i = 0;
        while (i < str.length) {
            const ch = str[i];
            if (ch === '"') {
                // Find end of string
                let j = i + 1;
                while (j < str.length) {
                    if (str[j] === '\\') { j += 2; continue; }
                    if (str[j] === '"') { j++; break; }
                    j++;
                }
                const s = str.slice(i, j);
                // Check if this is a key (followed by colon)
                let k = j;
                while (k < str.length && str[k] === ' ') k++;
                if (str[k] === ':') {
                    tokens.push(html`<span class="json-key">${s}</span>`);
                } else {
                    tokens.push(html`<span class="json-string">${s}</span>`);
                }
                i = j;
            } else if (ch === '-' || (ch >= '0' && ch <= '9')) {
                let j = i;
                while (j < str.length && /[\d.eE+\-]/.test(str[j])) j++;
                tokens.push(html`<span class="json-number">${str.slice(i, j)}</span>`);
                i = j;
            } else if (str.slice(i, i + 4) === 'true' || str.slice(i, i + 5) === 'false') {
                const word = str.slice(i, i + (str[i + 4] === 'e' ? 5 : 4));
                tokens.push(html`<span class="json-bool">${word}</span>`);
                i += word.length;
            } else if (str.slice(i, i + 4) === 'null') {
                tokens.push(html`<span class="json-null">null</span>`);
                i += 4;
            } else if (ch === '{' || ch === '}' || ch === '[' || ch === ']') {
                tokens.push(html`<span class="json-brace">${ch}</span>`);
                i++;
            } else {
                tokens.push(ch);
                i++;
            }
        }
        return tokens;
    }

    render() {
        const quickMethods = [
            ['HelloResource#hello', ''],
            ['HelloResource#hello', '{"name": "World"}'],
            ['HelloResource#hello', '{"name": "John", "surname": "Doe"}'],
            ['HelloResource#helloNonBlocking', '{"name": "World"}'],
            ['HelloResource#helloUni', '{"name": "Async"}'],
            ['HelloResource#helloUniBlocking', '{"name": "Blocking"}'],
            ['HelloResource#helloMulti', '{"name": "Stream"}'],
            ['HelloResource#helloMulti', ''],
            ['PojoResource#pojo', ''],
            ['PojoResource#pojo', '{"name": "John", "surname": "Doe"}'],
            ['PojoResource#pojoUni', ''],
            ['PojoResource#pojoMulti', ''],
            ['scoped#hello', ''],
            ['scoped#hello', '{"name": "World"}'],
            ['scoped#pojo', ''],
        ];

        return html`
            <h1>Quarkus JSON-RPC Tester
                <span class="status ${this._connected ? 'connected' : 'disconnected'}">
                    ${this._connected ? 'Connected' : 'Disconnected'}
                </span>
            </h1>

            <div class="layout">
                <div class="panel">
                    <h2>Request</h2>

                    <label>Quick methods</label>
                    <div class="quick-methods">
                        ${quickMethods.map(([m, p]) => {
                            const label = p ? `${m}(${Object.keys(JSON.parse(p)).join(', ')})` : `${m}()`;
                            return html`<button @click=${() => this._selectMethod(m, p)}>${label}</button>`;
                        })}
                    </div>

                    <label>Method</label>
                    <input type="text" .value=${this._method}
                        @input=${e => this._method = e.target.value}>

                    <label>Params (JSON)</label>
                    <textarea rows="3" .value=${this._params}
                        @input=${e => this._params = e.target.value}
                        placeholder='{"name": "value"} or ["value1", "value2"]'></textarea>

                    <div class="actions">
                        <button class="btn-send" ?disabled=${!this._connected} @click=${this._send}>Send</button>
                        <button class="btn-clear" @click=${this._clearMessages}>Clear</button>
                    </div>

                    ${this._subscriptions.size > 0 ? html`
                        <div class="subscriptions">
                            <label>Active subscriptions</label>
                            ${[...this._subscriptions.entries()].map(([id, method]) => html`
                                <div class="sub-item">
                                    <span>${method} (${id.substring(0, 8)}...)</span>
                                    <button @click=${() => this._unsubscribe(id)}>Unsubscribe</button>
                                </div>
                            `)}
                        </div>
                    ` : ''}
                </div>

                <div class="panel">
                    <h2>Messages (${this._messages.length})</h2>
                    <div class="messages">
                        ${this._messages.map(m => html`
                            <div class="msg ${m.type}">
                                <div class="msg-label ${m.type}">${m.type} ${m.time}</div>
                                ${this._renderJson(m.data)}
                            </div>
                        `)}
                    </div>
                </div>
            </div>
        `;
    }
}

customElements.define('jsonrpc-app', JsonRpcApp);
