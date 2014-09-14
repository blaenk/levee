Levee is a multi-user interface for rtorrent that strives for simplicity, written in Clojure (http-kit/compojure) and ClojureScript (om/react).

Levee has a clean, responsive UI which uses HTML5 WebSockets to keep up-to-date data. It supports drag-and-drop file uploads as well as magnet links. There's a simple lock-system to facilitate the mult-user environment by preventing members from removing items that others are still interested in.

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

# download project dependencies
$ lein deps

# build jar with all dependencies bundled
$ lein build
```

If you want, you can then package it up to use on a separate computer so that the only required dependency is a Java runtime:

``` bash
# create tarball with necessary files
$ lein package
```

# Usage

Once you have the jar file you can run levee via `bin/levee`. You can edit this to change configuration variables to your liking.

You should also merge the rtorrent configuration directives present in `etc/rtorrent.rc` into your own rtorrent configuration.

There's a sample systemd unit in `etc/levee@.service`.

## nginx

If you want to serve this behind nginx, which I recommend—especially to leverage [sendfile] for file downloads—you may want to use something like the following configuration. Don't forget to set `SENDFILE=yes` as well.

[sendfile]: http://wiki.nginx.org/XSendfile

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

