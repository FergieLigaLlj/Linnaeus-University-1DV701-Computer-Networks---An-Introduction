# Simple script to test the 1DV701 webserver. 

import requests
from pathlib import Path
import re
import hashlib

# Assumes the server runs on localhost, port 80. 
server = '127.0.0.1'
port = 80

# Points to the files themselves
dirbase = Path('./public')

# Allow for .htm solutions
fe = '.html'

def killWS(s):
    return re.sub(r'\s', '', s)

def loadPage(fn):
    s = open(dirbase / fn).read()
    return killWS(s)

def loadImage(fn):
    s = open(dirbase / fn, 'rb').read()
    return s

def requestPageOK(p, pn):
    # Assumes 'index.html' should be added if p is not a file
    if not (dirbase / p).is_file():
        fn = Path(p) / f'index{fe}'
    else:
        fn = p

    r = requests.get(f'http://{server}:{port}/{p}')

    if r.status_code != requests.codes.ok:
        print(f'Error: {pn} failed with code {r.status_code}')
        return False

    if r.headers['content-type'] != 'text/html':
        print(f'Error: {pn} has content type {r.headers["content-type"]}')
        return False

    # loads page from disk to compare with fetched page
    if killWS(r.text) != loadPage(fn):
        print(f'Error: {pn} not same as local copy')
        return False

    print(f'OK: {pn}')
    return True

def requestPageNotOK(p, pn, code):
    r = requests.get(f'http://{server}:{port}/{p}')

    if r.status_code == requests.codes.ok:
        print(f'Error: {pn} succeeded with code {r.status_code}')       
        return False

    if r.status_code != code:
        print(f'Error: {pn} expected code {code}, got {r.status_code}')
        return False    

    print(f'OK: {pn}')
    return True

def requestImage(p, pn):
    r = requests.get(f'http://{server}:{port}/{p}')

    if r.status_code != requests.codes.ok:
        print(f'Error: {pn} failed with code {r.status_code}')
        return False

    if r.headers['content-type'] != 'image/png' and r.headers['content-type'] != 'image/x-png':
        print(f'Error: {pn} has content type {r.headers["content-type"]}')
        return False

    ## (2022 ls223qx) - Kudos to Albert - ah225ex for finding a problem here and providing a fix!
    image = loadImage(p)
    if 'content-length' in r.headers:
        contentLengthVar = r.headers['content-length']
        if not r.headers['content-length'].isdigit():
            print(f'Error: {pn} content length not a number.')
            print(f'Got instead: {contentLengthVar}')
            return False
        elif int(r.headers['content-length']) != len(image):
            print(f'Error: {pn} content length incorrect')
            print(f'Image len is {len(image)} and contentlength is {contentLengthVar}.')
            return False
    else:
        print(f'Notice: {pn} has no content length header')

    image_h = hashlib.sha256()
    image_h.update(image)

    r_image_h = hashlib.sha256()
    r_image_h.update(r.content)

    if r_image_h.digest() != r_image_h.digest():
        print(f'Error: {pn} not same digest as local copy')

    print(f'OK: {pn}')
    return True

# Test main index
requestPageOK('', 'Main index page')

# Test named page
requestPageOK(f'named{fe}', 'Named page')

# Test named page
requestPageOK(f'named{fe}', 'Named page')

# Test image
requestImage('clown.png', 'Clown PNG')

# Test image in dir
requestImage('a/b/bee.png', 'Bee PNG')

# Test large image
requestImage('world.png', 'World PNG')

# Test directory structure (with index and named pages)
requestPageOK('a/', 'Index a')
requestPageOK(f'a/a{fe}', 'Page a')
requestPageOK(f'a/b{fe}', 'Fake page b')

requestPageOK('a/b/', 'Index b')
requestPageOK(f'a/b/b{fe}', 'Page b')
requestPageOK(f'a/b/c{fe}', 'Fake Page c')

requestPageOK('a/b/c', 'Index c')
requestPageOK(f'a/b/c/c{fe}', 'Page c')

# Test page that does not exist
requestPageNotOK(f'nosuchpage{fe}', 'Page fail', 404)
requestPageNotOK(f'a/fail{fe}', 'Page in dir fail', 404)
requestPageNotOK('a/b/c/d', 'Dir no index fail', 404)
requestPageNotOK('a/b/c/noimage.png', 'Image fail', 404)
