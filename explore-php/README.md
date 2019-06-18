# php 5.5 and below is vulnerable!

The script actually works - this will load Wikipedia:

```
docker run --rm --volume $PWD/:/src --workdir /src php:5.5-cli php main.php 'http://wikipedia.org' 10
```

Here's the bug: the same script loads stuff from Twitter, when it should be loading Wikipedia:

```
docker run --rm --volume $PWD/:/src --workdir /src php:5.5-cli php main.php 'http://wikipedia.org#@t.co' 10
```

It was patched among the containers between php 5.5 and 5.6:
(For the purposes of this talk I didn't figure out whether curl got patched, or php got patched)

```
docker run --rm --volume $PWD/:/src --workdir /src php:5.6-cli php main.php 'http://wikipedia.org#@t.co' 10
```
