# php 5.5 and below is vulnerable!

The script actually works - this will load 10 lines from Wikipedia:

```
docker run --rm --volume $PWD/:/src --workdir /src php:5.5-cli php main.php 'http://wikipedia.com' 10
```

Here's the bug: the same script loads stuff from Twitter, when it should be loading Wikipedia:

```
docker run --rm --volume $PWD/:/src --workdir /src php:5.5-cli php main.php 'http://wikipedia.com#@t.co' 10
```

It was patched somewhere between php 5.5 and 5.6:

```
docker run --rm --volume $PWD/:/src --workdir /src php:5.6-cli php main.php 'http://wikipedia.com#@t.co' 10
```
