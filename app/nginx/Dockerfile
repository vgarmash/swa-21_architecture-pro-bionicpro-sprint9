# Multi-stage build for nginx with ngx_cache_purge module
# Stage 1: Build nginx with ngx_cache_purge module
FROM nginx:1.25-alpine AS builder

# Install build dependencies
RUN apk add --no-cache \
    gcc \
    musl-dev \
    linux-headers \
    openssl-dev \
    pcre-dev \
    zlib-dev \
    git \
    wget \
    bash \
    make

# Set nginx version
ENV NGINX_VERSION=1.25.4

# Download and extract nginx source
RUN wget -q https://nginx.org/download/nginx-${NGINX_VERSION}.tar.gz && \
    tar -xzf nginx-${NGINX_VERSION}.tar.gz && \
    rm nginx-${NGINX_VERSION}.tar.gz

# Clone ngx_cache_purge module
RUN git clone https://github.com/FRiCKLE/ngx_cache_purge.git /tmp/ngx_cache_purge

# Configure and build nginx with ngx_cache_purge module
WORKDIR /nginx-${NGINX_VERSION}
RUN ./configure \
    --prefix=/etc/nginx \
    --sbin-path=/usr/sbin/nginx \
    --modules-path=/usr/lib/nginx/modules \
    --conf-path=/etc/nginx/nginx.conf \
    --error-log-path=/var/log/nginx/error.log \
    --http-log-path=/var/log/nginx/access.log \
    --pid-path=/var/run/nginx.pid \
    --lock-path=/var/run/nginx.lock \
    --http-client-body-temp-path=/var/cache/nginx/client_temp \
    --http-proxy-temp-path=/var/cache/nginx/proxy_temp \
    --http-fastcgi-temp-path=/var/cache/nginx/fastcgi_temp \
    --http-uwsgi-temp-path=/var/cache/nginx/uwsgi_temp \
    --http-scgi-temp-path=/var/cache/nginx/scgi_temp \
    --with-http_ssl_module \
    --with-http_v2_module \
    --with-http_realip_module \
    --with-http_addition_module \
    --with-http_sub_module \
    --with-http_dav_module \
    --with-http_flv_module \
    --with-http_mp4_module \
    --with-http_gunzip_module \
    --with-http_gzip_static_module \
    --with-http_random_index_module \
    --with-http_secure_link_module \
    --with-http_stub_status_module \
    --with-http_auth_request_module \
    --with-threads \
    --with-stream \
    --with-stream_ssl_module \
    --with-stream_realip_module \
    --with-stream_ssl_preread_module \
    --with-http_slice_module \
    --with-mail \
    --with-mail_ssl_module \
    --with-file-aio \
    --with-compat \
    --add-module=/tmp/ngx_cache_purge && \
    make -j$(nproc) && \
    make install

# Stage 2: Final minimal image
FROM nginx:1.25-alpine

# Install runtime dependencies
RUN apk add --no-cache pcre zlib openssl

# Copy nginx binary from builder
COPY --from=builder /usr/sbin/nginx /usr/sbin/nginx
COPY --from=builder /usr/lib/nginx/modules /usr/lib/nginx/modules
COPY --from=builder /etc/nginx /etc/nginx

# Create cache directories
RUN mkdir -p /var/cache/nginx/reports && \
    mkdir -p /var/run && \
    chown -R nginx:nginx /var/cache/nginx

# Expose port
EXPOSE 80

# Default command
CMD ["nginx", "-g", "daemon off;"]
