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

function get_lines($content, $n_lines) {
    if ($n_lines) {
        $lines = explode(PHP_EOL, $content);
        return implode(PHP_EOL, array_slice($lines, 0, $n_lines)) . PHP_EOL;
    } else {
        return $content;
    }
}

function main($argv) {
    $url = $argv[1];
    $lines = intval($argv[2]);

    $parsed = parse_url($url);

    print_r($parsed);

    if (ends_with($parsed["host"], "wikipedia.com")) {
        print_r(get_lines(curl_get_contents($url), $lines));
    } else {
        die("You Shall Not Pass\n");
    }
}

main($argv);
