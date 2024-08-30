package io.quarkiverse.jsonrpc.openrpc.schema;

public record Info(String title, String description, String termsOfService, Contact contact, License license,
        String version) {

    @Override
    public String toString() {
        return "Info[" +
                "title=" + title + ", " +
                "description=" + description + ", " +
                "termsOfService=" + termsOfService + ", " +
                "contact=" + contact + ", " +
                "license=" + license + ", " +
                "version=" + version + ']';
    }

}
