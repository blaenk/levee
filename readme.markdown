Levee is a multi-user interface for rtorrent that strives for simplicity.

# Build

[Leiningen] is required to build the project, which itself requires the Java Development Kit (JDK). [node.js] is needed for [bower] and [grunt] to build the stylesheet.

[Leiningen]: http://leiningen.org/
[node.js]: http://nodejs.org
[bower]: http://bower.io/
[grunt]: http://gruntjs.com/

``` bash
# install bower and grunt
$ sudo npm install -g bower grunt-cli

$ git clone https://github.com/blaenk/levee
$ cd levee

# fetch bootstrap
$ bower install

# compile css
$ grunt less

# download project dependencies
$ lein deps

# compile clojurescript to javascript
$ lein cljsbuild once prod

# build jar with all dependencies bundled
$ lein uberjar
```

# Usage

Once you have the jar file you can run levee via `bin/levee`. You can edit this to change configuration variables to your liking.

You should also merge the rtorrent configuration directives present in `etc/rtorrent.rc` into your own rtorrent configuration.

There's a sample systemd unit in `etc/levee@.service`.

## nginx

If you want to serve this behind nginx, which I recommend—especially to leverage X-Accel-Redirect for file downloads—you may want to use something like the following configuration. Don't forget to set `SENDFILE=yes` as well.

```
http {
  # handle levee's websockets
  map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
  }

  upstream levee {
    server 127.0.0.1:3000 fail_timeout=0;
    keepalive 32;
  }

  server {
    root /path/to/levee/resources/public;
    charset utf-8;
    client_max_body_size 10M;

    location /sendfile/ {
      internal;
      alias /path/to/rtorrent/downloads;
    }

    location / {
      expires max;
      add_header Cache-Control public;
      try_files $uri @levee;
    }

    location @levee {
      proxy_redirect off;
      proxy_buffering off;
      proxy_set_header Host $http_host;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
      proxy_pass http://levee;
      proxy_http_version 1.1;
      proxy_set_header Upgrade $http_upgrade;
      proxy_set_header Connection $connection_upgrade;
    }
  }
}
```

