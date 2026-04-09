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
    `;

    render() {
        return html`
            <vaadin-grid .items=${methods} class="methods-grid" theme="no-border">
                <vaadin-grid-sort-column path="key" header="Key" auto-width resizable></vaadin-grid-sort-column>
                <vaadin-grid-sort-column path="className" header="Class" auto-width resizable></vaadin-grid-sort-column>
                <vaadin-grid-sort-column path="methodName" header="Method" auto-width resizable></vaadin-grid-sort-column>
                <vaadin-grid-sort-column path="parameters" header="Parameters" auto-width resizable></vaadin-grid-sort-column>
                <vaadin-grid-sort-column path="executionMode" header="Execution Mode" auto-width resizable></vaadin-grid-sort-column>
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
