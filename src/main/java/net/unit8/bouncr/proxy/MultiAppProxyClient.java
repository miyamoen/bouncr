package net.unit8.bouncr.proxy;

import enkan.middleware.session.KeyValueStore;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.UndertowClient;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.proxy.ProxyCallback;
import io.undertow.server.handlers.proxy.ProxyClient;
import io.undertow.server.handlers.proxy.ProxyConnection;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HttpString;
import net.unit8.bouncr.authz.UserPermissionPrincipal;
import net.unit8.bouncr.component.BouncrConfiguration;
import net.unit8.bouncr.component.RealmCache;
import net.unit8.bouncr.web.entity.Application;
import net.unit8.bouncr.web.entity.Realm;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

import java.io.IOException;
import java.nio.channels.Channel;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static enkan.util.ThreadingUtils.some;

/**
 * @author kawasima
 */
public class MultiAppProxyClient implements ProxyClient {
    private static final ProxyTarget PROXY_TARGET = new ProxyTarget() {
    };
    private final AttachmentKey<ClientConnection> clientAttachmentKey = AttachmentKey.create(ClientConnection.class);
    private final UndertowClient client;
    private final KeyValueStore store;
    private final RealmCache realmCache;
    private final BouncrConfiguration config;

    public MultiAppProxyClient(BouncrConfiguration config, KeyValueStore store, RealmCache realmCache) {
        client = UndertowClient.getInstance();
        this.config = config;
        this.store = store;
        this.realmCache = realmCache;
    }

    @Override
    public ProxyTarget findTarget(HttpServerExchange exchange) {
        return PROXY_TARGET;
    }

    @Override
    public void getConnection(ProxyTarget target, HttpServerExchange exchange, ProxyCallback<ProxyConnection> callback, long timeout, TimeUnit timeUnit) {
        Realm realm = realmCache.matches(exchange.getRequestPath());
        if (realm != null) {
            parseToken(exchange).ifPresent(token -> {
                Optional<UserPermissionPrincipal> principal = authenticate(realm.getId(), token);
                principal.ifPresent(p -> {
                    exchange.getRequestHeaders().put(HttpString.tryFromString(config.getIdHeaderName()), p.getName());
                    exchange.getRequestHeaders().put(HttpString.tryFromString(config.getPermissionHeaderName()), String.join(",", p.getPermissions()));
                });
            });
        } else {
            exchange.setStatusCode(404);
            exchange.endExchange();
            return;
        }

        Application application = realmCache.getApplication(realm);
        ClientConnection existing = exchange.getConnection().getAttachment(clientAttachmentKey);
        if (existing != null) {
            if (existing.isOpen()) {
                //this connection already has a client, re-use it
                String path = exchange.getRequestPath();
                if (path.startsWith(application.getVirtualPath())) {
                    exchange.setRequestPath(path.substring(application.getVirtualPath().length(), path.length()));
                    exchange.setRequestURI(path.substring(application.getVirtualPath().length(), path.length()));
                }
                callback.completed(exchange, new ProxyConnection(existing, "/"));
                return;
            } else {
                exchange.getConnection().removeAttachment(clientAttachmentKey);
            }
        }

        client.connect(new ConnectNotifier(callback, exchange),
                application.getUriToPass(),
                exchange.getIoThread(),
                exchange.getConnection().getByteBufferPool(), OptionMap.EMPTY);
    }

    private Optional<String> parseToken(HttpServerExchange exchange) {
        if (exchange.getRequestHeaders().contains("Authorization")) {
            String authorizationValue = exchange.getRequestHeaders().getFirst("Authorization");
            String[] tokens = authorizationValue.split("\\s+");
            if (Objects.equals(tokens[0], "Bearer")) {
                return Optional.of(tokens[1]);
            } else {
                return Optional.empty();
            }
        } else if (exchange.getRequestCookies().containsKey(config.getTokenName())) {
            Cookie tokenCookie = exchange.getRequestCookies().get(config.getTokenName());
            return Optional.of(tokenCookie.getValue());
        } else {
            return Optional.empty();
        }
    }

    private Optional<UserPermissionPrincipal> authenticate(Long realmId, String token) {
        return some((Map<Long, UserPermissionPrincipal>) store.read(token),
                permsByRealm -> permsByRealm.get(realmId));
    }

    private final class ConnectNotifier implements ClientCallback<ClientConnection> {
        private final ProxyCallback<ProxyConnection> callback;
        private final HttpServerExchange exchange;

        private ConnectNotifier(ProxyCallback<ProxyConnection> callback, HttpServerExchange exchange) {
            this.callback = callback;
            this.exchange = exchange;
        }

        @Override
        public void completed(final ClientConnection connection) {
            final ServerConnection serverConnection = exchange.getConnection();
            //we attach to the connection so it can be re-used
            serverConnection.putAttachment(clientAttachmentKey, connection);
            serverConnection.addCloseListener(serverConnection1 -> IoUtils.safeClose(connection));
            connection.getCloseSetter().set((ChannelListener<Channel>) channel -> serverConnection.removeAttachment(clientAttachmentKey));

            exchange.setRelativePath("/");
            Realm realm = realmCache.matches(exchange.getRequestPath());
            Application application = realmCache.getApplication(realm);
            String path = exchange.getRequestPath();
            if (path.startsWith(application.getVirtualPath())) {
                exchange.setRequestPath(path.substring(application.getVirtualPath().length(), path.length()));
                exchange.setRequestURI(path.substring(application.getVirtualPath().length(), path.length()));
            }
            callback.completed(exchange, new ProxyConnection(connection, "/"));
        }

        @Override
        public void failed(IOException e) {
            callback.failed(exchange);
        }
    }

}
