import { LitElement, html, css } from 'lit';
import { methods } from 'build-time-data';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/grid';
import '@vaadin/grid/vaadin-grid-sort-column.js';
import { RouterController } from 'router-controller';

export class QwcJsonRpcMethods extends LitElement {

    routerController = new RouterController(this);

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            height: 100%;
        }
        .methods-grid {
            height: 100%;
        }
        .action-link {
            cursor: pointer;
            color: var(--lumo-primary-color);
            font-weight: 500;
        }
        .action-link:hover {
            text-decoration: underline;
        }
        .security-badge {
            display: inline-block;
            padding: 2px 8px;
            border-radius: 10px;
            font-size: 0.8em;
            font-weight: 500;
        }
        .security-badge.secured {
            background: var(--lumo-primary-color-10pct, #e3f2fd);
            color: var(--lumo-primary-text-color, #1565c0);
        }
        .security-badge.unsecured {
            color: var(--lumo-secondary-text-color, #999);
            font-style: italic;
        }
    `;

    render() {
        return html`
            <vaadin-grid .items=${methods} class="methods-grid" theme="no-border">
                <vaadin-grid-sort-column path="key" header="Key" auto-width resizable></vaadin-grid-sort-column>
                <vaadin-grid-sort-column path="className" header="Class" auto-width resizable></vaadin-grid-sort-column>
                <vaadin-grid-sort-column path="methodName" header="Method" auto-width resizable></vaadin-grid-sort-column>
                <vaadin-grid-sort-column path="parameters" header="Parameters" auto-width resizable></vaadin-grid-sort-column>
                <vaadin-grid-sort-column path="executionMode" header="Execution Mode" auto-width resizable></vaadin-grid-sort-column>
                <vaadin-grid-column header="Security" auto-width resizable
                    ${columnBodyRenderer((item) => item.security
                        ? html`<span class="security-badge secured">${item.security}</span>`
                        : html`<span class="security-badge unsecured">none</span>`
                    , [])}>
                </vaadin-grid-column>
                <vaadin-grid-column
                    frozen-to-end
                    auto-width
                    flex-grow="0"
                    ${columnBodyRenderer((item) => html`
                        <a class="action-link" @click=${() => this._testMethod(item.key)}>Test</a>
                    `, [])}>
                </vaadin-grid-column>
            </vaadin-grid>
        `;
    }

    _testMethod(key) {
        sessionStorage.setItem('jsonrpc-test-method', key);
        const pages = this.routerController.getPagesForCurrentNamespace();
        const testerPage = pages.find(p => p.title === 'Tester');
        if (testerPage) {
            this.routerController.go(testerPage);
        }
    }
}

customElements.define('qwc-json-rpc-methods', QwcJsonRpcMethods);
