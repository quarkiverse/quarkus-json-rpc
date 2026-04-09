import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';

export class QwcJsonRpcSessions extends LitElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: 1em;
            padding: 1em;
        }
        .toolbar {
            display: flex;
            align-items: center;
            gap: 1em;
        }
        button {
            cursor: pointer;
            background: var(--lumo-primary-color);
            color: var(--lumo-primary-contrast-color);
            border: none;
            padding: 0.5em 1.2em;
            border-radius: var(--lumo-border-radius-s);
            font-weight: 500;
        }
        button:hover {
            opacity: 0.9;
        }
        .summary {
            font-size: 0.9em;
            color: var(--lumo-secondary-text-color);
        }
        table {
            width: 100%;
            border-collapse: collapse;
        }
        th, td {
            text-align: left;
            padding: 0.5em 0.75em;
            border-bottom: 1px solid var(--lumo-contrast-10pct);
        }
        th {
            font-weight: 600;
            color: var(--lumo-secondary-text-color);
            font-size: 0.85em;
            text-transform: uppercase;
        }
        .empty-state {
            color: var(--lumo-secondary-text-color);
            font-style: italic;
            padding: 2em;
            text-align: center;
        }
        h3 {
            margin: 0.5em 0 0.25em 0;
        }
    `;

    static properties = {
        _sessions: { state: true },
        _subscriptions: { state: true },
        _loading: { state: true },
    };

    constructor() {
        super();
        this._sessions = [];
        this._subscriptions = [];
        this._loading = false;
    }

    connectedCallback() {
        super.connectedCallback();
        this._refresh();
    }

    render() {
        return html`
            <div class="toolbar">
                <button @click=${this._refresh} ?disabled=${this._loading}>
                    ${this._loading ? 'Loading...' : 'Refresh'}
                </button>
                <span class="summary">
                    ${this._sessions.length} session(s), ${this._subscriptions.length} subscription(s)
                </span>
            </div>

            <h3>Sessions</h3>
            ${this._sessions.length > 0 ? html`
                <table>
                    <thead>
                        <tr>
                            <th>Session ID</th>
                            <th>Subscriptions</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${this._sessions.map(s => html`
                            <tr>
                                <td><code>${s.sessionId}</code></td>
                                <td>${s.subscriptionCount}</td>
                            </tr>
                        `)}
                    </tbody>
                </table>
            ` : html`
                <div class="empty-state">No active sessions</div>
            `}

            <h3>Subscriptions</h3>
            ${this._subscriptions.length > 0 ? html`
                <table>
                    <thead>
                        <tr>
                            <th>Subscription ID</th>
                            <th>Session ID</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${this._subscriptions.map(s => html`
                            <tr>
                                <td><code>${s.subscriptionId}</code></td>
                                <td><code>${s.sessionId}</code></td>
                            </tr>
                        `)}
                    </tbody>
                </table>
            ` : html`
                <div class="empty-state">No active subscriptions</div>
            `}
        `;
    }

    _refresh() {
        this._loading = true;
        Promise.all([
            this.jsonRpc.listSessions().then(r => { this._sessions = r.result || []; }),
            this.jsonRpc.listSubscriptions().then(r => { this._subscriptions = r.result || []; }),
        ]).finally(() => {
            this._loading = false;
        });
    }
}

customElements.define('qwc-json-rpc-sessions', QwcJsonRpcSessions);
