#!/usr/bin/env ipython3
"""
Send test data to Redis

Usage:
   send.py -n=<number> -u=<unique> [-j] [-p]
   send.py -h | --help

Options:
   -h, --help        Show this screen
   -j                Save data as JSON string using SET command
                     If -j is not specified then save data as hash
   -p                Issue PUBLISH too, note this can only be used when -j is specified
   -n=<number>       Number of test data
   -u=<unique>       Number of unique tickers
"""
import json
import redis

p = None


def save(uniq, num, save_to_redis, publish):
    for i in range(num):
        save_to_redis({'ticker': 'ticker{}'.format(i % uniq), 'price': i}, publish)


def save_as_json(tick, publish):
    global p
    global e
    j = json.dumps(tick)
    ticker = tick['ticker']
    p.set(ticker, j)
    if publish:
        p.publish(ticker, j)
    p.execute()


def save_as_hash(tick, publish):
    global p
    p.hmset(tick['ticker'], tick)
    p.execute()


if __name__ == "__main__":
    from docopt import docopt
    from timeit import default_timer as timer
    args = docopt(__doc__)

    if args['-p'] and not args['-j']:
        print('-p can only be used when -j is specified')
    else:
        r = redis.Redis(host='localhost', port=6379, db=0)
        p = r.pipeline()
        start = timer()
        save(int(args['-u']), int(args['-n']), save_as_json if args['-j'] else save_as_hash, args['-p'])
        end = timer()
        print(end - start)
