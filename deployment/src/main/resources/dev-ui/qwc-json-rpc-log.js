import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import { LogController } from 'log-controller';

export class QwcJsonRpcLog extends LitElement {

    jsonRpc = new JsonRpc(this);
    logControl = new LogController(this);

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            height: 100%;
        }
        .log-container {
            flex: 1;
            overflow-y: auto;
            font-family: 'JetBrains Mono', 'Fira Code', monospace;
            font-size: 0.78em;
            line-height: 1.5;
        }
        .entry {
            padding: 4px 10px;
            border-bottom: 1px solid var(--lumo-contrast-5pct);
            display: flex;
            gap: 8px;
            align-items: flex-start;
        }
        .entry.incoming {
            background: var(--lumo-primary-color-10pct, #e3f2fd);
        }
        .entry.outgoing {
            background: var(--lumo-contrast-5pct);
        }
        .entry.error {
            background: var(--lumo-error-color-10pct, #ffebee);
        }
        .direction {
            flex: 0 0 16px;
            text-align: center;
        }
        .direction.incoming {
            color: var(--lumo-primary-color, #1565c0);
        }
        .direction.outgoing {
            color: var(--lumo-success-color, #2e7d32);
        }
        .time {
            flex: 0 0 auto;
            color: var(--lumo-secondary-text-color);
            font-size: 0.9em;
        }
        .session {
            flex: 0 0 auto;
            color: var(--lumo-tertiary-text-color, #999);
            font-size: 0.9em;
        }
        .message {
            flex: 1;
            white-space: pre-wrap;
            word-break: break-all;
        }
        .empty-state {
            display: flex;
            align-items: center;
            justify-content: center;
            height: 100%;
            color: var(--lumo-secondary-text-color);
            font-style: italic;
            font-size: 0.85em;
        }
    `;

    static properties = {
        _messages: { state: true },
    };

    constructor() {
        super();
        this._messages = [];
        this._observer = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this._subscribe();
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        this._unsubscribe();
    }

    _subscribe() {
        if (this._observer) return;
        this._observer = this.jsonRpc.streamLog()
            .onNext(entry => {
                const data = entry.result || entry;
                const updated = [...this._messages, data];
                this._messages = updated.length > 500 ? updated.slice(-500) : updated;
                this.updateComplete.then(() => {
                    const container = this.shadowRoot?.querySelector('.log-container');
                    if (container) container.scrollTop = container.scrollHeight;
                });
            });
    }

    _unsubscribe() {
        if (this._observer) {
            this._observer.cancel();
            this._observer = null;
        }
    }

    _formatTime(timestamp) {
        try {
            return new Date(timestamp).toLocaleTimeString();
        } catch (e) {
            return timestamp;
        }
    }

    _truncateSession(sessionId) {
        if (!sessionId) return '';
        return sessionId.substring(0, 8);
    }

    _isError(msg) {
        try {
            const parsed = JSON.parse(msg);
            return parsed.error !== undefined && parsed.error !== null;
        } catch (e) {
            return false;
        }
    }

    _formatJson(msg) {
        try {
            return JSON.stringify(JSON.parse(msg), null, 2);
        } catch (e) {
            return msg;
        }
    }

    render() {
        if (this._messages.length === 0) {
            return html`<div class="empty-state">Waiting for JSON-RPC messages on /json-rpc ...</div>`;
        }
        return html`
            <div class="log-container">
                ${this._messages.map(m => {
                    const isError = this._isError(m.message);
                    const entryClass = isError ? 'entry error'
                        : m.direction === 'incoming' ? 'entry incoming' : 'entry outgoing';
                    return html`
                        <div class="${entryClass}">
                            <span class="direction ${m.direction}">
                                ${m.direction === 'incoming' ? '⬆' : '⬇'}
                            </span>
                            <span class="time">${this._formatTime(m.timestamp)}</span>
                            <span class="session">${this._truncateSession(m.sessionId)}</span>
                            <span class="message">${this._formatJson(m.message)}</span>
                        </div>
                    `;
                })}
            </div>
        `;
    }
}

customElements.define('qwc-json-rpc-log', QwcJsonRpcLog);
