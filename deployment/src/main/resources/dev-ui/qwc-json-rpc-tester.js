import { LitElement, html, css } from 'lit';
import { methods } from 'build-time-data';
import { endpointPath } from 'build-time-data';
import { JsonRpc } from 'jsonrpc';

export class QwcJsonRpcTester extends LitElement {

    static _nextRequestId = 0;
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
            min-width: 120px;
            font-weight: bold;
        }
        .param-inputs {
            display: flex;
            flex-direction: column;
            gap: 0.5em;
            margin-left: 120px;
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
        select, input, button {
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
    `;

    static properties = {
        _selectedMethod: { state: true },
        _paramValues: { state: true },
        _result: { state: true },
        _streaming: { state: true },
        _streamItems: { state: true },
    };

    constructor() {
        super();
        this._selectedMethod = null;
        this._paramValues = {};
        this._result = null;
        this._streaming = false;
        this._streamItems = [];
    }

    connectedCallback() {
        super.connectedCallback();
        if (methods && methods.length > 0) {
            this._selectedMethod = methods[0];
        }
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        this._cancelStream();
    }

    render() {
        return html`
            <span class="endpoint-info">Endpoint: <code>${endpointPath}</code></span>

            <div class="form-row">
                <label>Method:</label>
                <select @change=${this._onMethodSelect}>
                    ${methods.map(m => html`
                        <option value=${m.key} ?selected=${this._selectedMethod?.key === m.key}>
                            ${m.key}
                        </option>
                    `)}
                </select>
            </div>

            ${this._selectedMethod?.parameters?.length > 0 ? html`
                <div class="param-inputs">
                    ${this._selectedMethod.parameters.split(', ').map(p => {
                        const [name] = p.split(':').map(s => s.trim());
                        return html`
                            <div class="form-row">
                                <label>${name} <small>(${p.split(':')[1]?.trim() || ''})</small>:</label>
                                <input type="text"
                                    .value=${this._paramValues[name] || ''}
                                    @input=${e => this._onParamInput(name, e.target.value)}
                                    placeholder="${name}">
                            </div>
                        `;
                    })}
                </div>
            ` : ''}

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

    _onMethodSelect(e) {
        const key = e.target.value;
        this._selectedMethod = methods.find(m => m.key === key);
        this._paramValues = {};
        this._result = null;
        this._streamItems = [];
        this._cancelStream();
    }

    _onParamInput(name, value) {
        this._paramValues = { ...this._paramValues, [name]: value };
    }

    _invoke() {
        this._result = null;
        this._streamItems = [];
        this._cancelStream();

        const params = {};
        if (this._selectedMethod?.parameters) {
            this._selectedMethod.parameters.split(', ').forEach(p => {
                const name = p.split(':')[0].trim();
                let val = this._paramValues[name] || '';
                // Try to parse as JSON for non-string values
                try {
                    val = JSON.parse(val);
                } catch (e) {
                    // keep as string
                }
                params[name] = val;
            });
        }

        // Use the Dev UI jsonrpc to call our service's listMethods,
        // but the actual invocation goes through the extension's own WS.
        // For the tester, we connect directly via WebSocket.
        this._invokeViaWebSocket(params);
    }

    _invokeViaWebSocket(params) {
        const loc = window.location;
        const wsProtocol = loc.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${wsProtocol}//${loc.host}${endpointPath}`;

        const ws = new WebSocket(wsUrl);
        const method = this._selectedMethod.key;
        const requestId = ++QwcJsonRpcTester._nextRequestId;

        ws.onopen = () => {
            const request = {
                jsonrpc: '2.0',
                id: requestId,
                method: method,
            };
            if (Object.keys(params).length > 0) {
                request.params = params;
            }
            ws.send(JSON.stringify(request));
        };

        ws.onmessage = (event) => {
            const data = JSON.parse(event.data);
            if (data.id === requestId && data.result !== undefined) {
                // Check if the result is a subscription ID (UUID pattern)
                if (typeof data.result === 'string' &&
                    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(data.result)) {
                    this._streaming = true;
                    this._subscriptionId = data.result;
                    this._ws = ws;
                    this._result = `Subscription started: ${data.result}`;
                    return; // Keep connection open for streaming
                }
                this._result = data.result;
                ws.close();
            } else if (data.id === requestId && data.error) {
                this._result = `Error: ${data.error.message} (code: ${data.error.code})`;
                ws.close();
            } else if (data.method === 'subscription' && data.params) {
                // Streaming item
                if (data.params.subscription === this._subscriptionId) {
                    if (data.params.result !== undefined) {
                        this._streamItems = [...this._streamItems, data.params.result];
                    }
                    if (data.params.complete) {
                        this._streaming = false;
                        this._result = 'Stream completed';
                        ws.close();
                    }
                    if (data.params.error) {
                        this._streaming = false;
                        this._result = `Stream error: ${data.params.error}`;
                        ws.close();
                    }
                }
            }
        };

        ws.onerror = () => {
            this._result = 'WebSocket connection error';
            ws.close();
        };
    }

    _cancelStream() {
        if (this._ws && this._subscriptionId) {
            const request = {
                jsonrpc: '2.0',
                id: ++QwcJsonRpcTester._nextRequestId,
                method: 'unsubscribe',
                params: [this._subscriptionId],
            };
            try {
                this._ws.send(JSON.stringify(request));
                this._ws.close();
            } catch (e) {
                // ignore if already closed
            }
        }
        this._streaming = false;
        this._subscriptionId = null;
        this._ws = null;
    }
}

customElements.define('qwc-json-rpc-tester', QwcJsonRpcTester);
