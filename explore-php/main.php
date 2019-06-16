<?php

function ends_with($haystack, $needle) {
    $len = strlen($needle);
    return (substr($haystack, -$len) === $needle);
}

function curl_get_contents($url) {
    $curl = curl_init($url);
    curl_setopt($curl, CURLOPT_RETURNTRANSFER, 1);
    curl_setopt($curl, CURLOPT_FOLLOWLOCATION, 1);
    curl_setopt($curl, CURLOPT_SSL_VERIFYPEER, 0);
    curl_setopt($curl, CURLOPT_SSL_VERIFYHOST, 0);
    $data = curl_exec($curl);
    curl_close($curl);
    return $data;
}

function get_title($content) {
    preg_match('/<title>.*?<\/title>/i', $content, $matches);
    return $matches[0];
}

function main($argv) {
    $url = $argv[1];

    $parsed = parse_url($url);

    print_r($parsed);

    if (ends_with($parsed["host"], "wikipedia.org")) {
        print_r(get_title(curl_get_contents($url)));
    } else {
        die("You Shall Not Pass\n");
    }
}

main($argv);
