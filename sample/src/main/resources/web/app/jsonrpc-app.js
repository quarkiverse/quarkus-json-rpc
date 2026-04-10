import {LitElement, html, css} from 'lit';

class JsonRpcApp extends LitElement {

    static properties = {
        _connected: {state: true},
        _messages: {state: true},
        _subscriptions: {state: true},
        _ready: {state: true},
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
            margin-top: 12px;
        }

        .actions button {
            padding: 8px 20px;
            border: none;
            border-radius: 4px;
            font-size: 0.9rem;
            font-weight: 500;
            cursor: pointer;
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
        this._messages = [];
        this._subscriptions = new Map();
        this._ready = false;
        this._rpc = null;
    }

    async connectedCallback() {
        super.connectedCallback();
        // Load the generated JSON-RPC proxy at runtime (dynamic path prevents esbuild resolution)
        const apiPath = ['/_static/quarkus-json-rpc-api', 'jsonrpc-api.js'].join('/');
        const rpc = await import(apiPath);
        this._rpc = rpc;
        rpc.client.onOpen = () => { this._connected = true; };
        rpc.client.onClose = () => {
            this._connected = false;
            this._subscriptions = new Map();
        };
        this._connected = rpc.client.connected;
        this._ready = true;
    }

    _addMessage(type, data) {
        const updated = [...this._messages, {type, data, time: new Date().toLocaleTimeString()}];
        this._messages = updated.length > 500 ? updated.slice(-500) : updated;
        this.updateComplete.then(() => {
            const container = this.shadowRoot.querySelector('.messages');
            if (container) container.scrollTop = container.scrollHeight;
        });
    }

    async _call(label, fn) {
        this._addMessage('sent', {call: label});
        try {
            const result = await fn();
            this._addMessage('received', {result});
        } catch (err) {
            this._addMessage('error', {error: err});
        }
    }

    _subscribe(label, fn) {
        this._addMessage('sent', {subscribe: label});
        const sub = fn()
            .onItem(item => this._addMessage('notification', {subscription: label, item}))
            .onError(err => {
                this._addMessage('error', {subscription: label, error: err});
                this._removeSubscription(label);
            })
            .onComplete(() => {
                this._addMessage('notification', {subscription: label, complete: true});
                this._removeSubscription(label);
            });
        this._subscriptions = new Map([...this._subscriptions, [label, sub]]);
    }

    _removeSubscription(label) {
        const subs = new Map(this._subscriptions);
        subs.delete(label);
        this._subscriptions = subs;
    }

    async _unsubscribe(label) {
        const sub = this._subscriptions.get(label);
        if (sub) {
            await sub.cancel();
            this._removeSubscription(label);
        }
    }

    _clearMessages() {
        this._messages = [];
    }

    _renderJson(obj) {
        const jsonStr = JSON.stringify(obj, null, 2);
        return html`<div class="json-block">${this._highlightJson(jsonStr)}</div>`;
    }

    _highlightJson(str) {
        const tokens = [];
        let i = 0;
        while (i < str.length) {
            const ch = str[i];
            if (ch === '"') {
                let j = i + 1;
                while (j < str.length) {
                    if (str[j] === '\\') { j += 2; continue; }
                    if (str[j] === '"') { j++; break; }
                    j++;
                }
                const s = str.slice(i, j);
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
        if (!this._ready) {
            return html`<h1>Loading...</h1>`;
        }

        const {HelloResource, PojoResource, scoped} = this._rpc;

        const calls = [
            ['HelloResource.hello()', () => HelloResource.hello()],
            ['HelloResource.hello({name})', () => HelloResource.hello({name: 'World'})],
            ['HelloResource.hello({name, surname})', () => HelloResource.hello({name: 'John', surname: 'Doe'})],
            ['HelloResource.helloNonBlocking({name})', () => HelloResource.helloNonBlocking({name: 'World'})],
            ['HelloResource.helloUni({name})', () => HelloResource.helloUni({name: 'Async'})],
            ['HelloResource.helloUniBlocking({name})', () => HelloResource.helloUniBlocking({name: 'Blocking'})],
            ['PojoResource.pojo()', () => PojoResource.pojo()],
            ['PojoResource.pojo({name, surname})', () => PojoResource.pojo({name: 'John', surname: 'Doe'})],
            ['PojoResource.pojoUni()', () => PojoResource.pojoUni()],
            ['scoped.hello()', () => scoped.hello()],
            ['scoped.hello({name})', () => scoped.hello({name: 'World'})],
            ['scoped.pojo()', () => scoped.pojo()],
        ];

        const streams = [
            ['HelloResource.helloMulti()', () => HelloResource.helloMulti()],
            ['HelloResource.helloMulti({name})', () => HelloResource.helloMulti({name: 'Stream'})],
            ['PojoResource.pojoMulti()', () => PojoResource.pojoMulti()],
        ];

        return html`
            <h1>Quarkus JSON-RPC Tester
                <span class="status ${this._connected ? 'connected' : 'disconnected'}">
                    ${this._connected ? 'Connected' : 'Disconnected'}
                </span>
            </h1>

            <div class="layout">
                <div class="panel">
                    <h2>RPC Calls</h2>
                    <label>Click a method to invoke it</label>
                    <div class="quick-methods">
                        ${calls.map(([label, fn]) =>
                            html`<button ?disabled=${!this._connected}
                                         @click=${() => this._call(label, fn)}>${label}</button>`
                        )}
                    </div>

                    <h2>Streaming</h2>
                    <label>Subscribe to a Multi stream</label>
                    <div class="quick-methods">
                        ${streams.map(([label, fn]) =>
                            html`<button ?disabled=${!this._connected}
                                         @click=${() => this._subscribe(label, fn)}>${label}</button>`
                        )}
                    </div>

                    ${this._subscriptions.size > 0 ? html`
                        <div class="subscriptions">
                            <label>Active subscriptions</label>
                            ${[...this._subscriptions.keys()].map(label => html`
                                <div class="sub-item">
                                    <span>${label}</span>
                                    <button @click=${() => this._unsubscribe(label)}>Unsubscribe</button>
                                </div>
                            `)}
                        </div>
                    ` : ''}

                    <div class="actions">
                        <button class="btn-clear" @click=${this._clearMessages}>Clear Messages</button>
                    </div>
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
