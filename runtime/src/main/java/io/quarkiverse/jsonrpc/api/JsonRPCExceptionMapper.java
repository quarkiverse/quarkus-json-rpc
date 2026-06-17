package io.quarkiverse.jsonrpc.api;

/**
 * Maps exceptions thrown by JSON-RPC methods to structured error responses.
 *
 * <p>
 * Implement this interface as a CDI bean to customize how exceptions are translated
 * into JSON-RPC error codes, messages, and optional data. Return {@code null} to
 * indicate that this mapper does not handle the given exception.
 *
 * <pre>
 * &#64;ApplicationScoped
 * public class MyExceptionMapper implements JsonRPCExceptionMapper {
 *
 *     &#64;Override
 *     public JsonRPCError mapException(Throwable exception) {
 *         if (exception instanceof OrderNotFoundException e) {
 *             return new JsonRPCError(-40001, "Order not found", e.getOrderId());
 *         }
 *         return null;
 *     }
 * }
 * </pre>
 */
public interface JsonRPCExceptionMapper {

    /**
     * Map the given exception to a JSON-RPC error, or return {@code null}
     * to let the next mapper (or built-in logic) handle it.
     */
    JsonRPCError mapException(Throwable exception);
}
