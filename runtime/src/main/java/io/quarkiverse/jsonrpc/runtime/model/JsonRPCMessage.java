package io.quarkiverse.jsonrpc.runtime.model;

/**
 * Allows JSON RPC methods to response with more finer grade message types
 *
 * @param <T> The type of the response object
 */
public class JsonRPCMessage<T> {
    private T response;
    private MessageType messageType;

    public JsonRPCMessage() {
    }

    public JsonRPCMessage(T response, MessageType messageType) {
        this.response = response;
        this.messageType = messageType;
    }

    public T getResponse() {
        return response;
    }

    public void setResponse(T response) {
        this.response = response;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }
}
