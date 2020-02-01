#!/usr/bin/env python3
"""
Recieve test data from Redis

Usage:
   rcv.py -n=<number> [-p]
   rcv.py -h | --help

Options:
   -h, --help        Show this screen
   -j                The data type is JSON string
                     If -j is not specified then the data type is bash
   -n=<number>       Number of test data
"""
import redis
import threading

start = None
tot = 0
rcv_cnt = 0


class myThread (threading.Thread):
    def __init__(self, publish):
        threading.Thread.__init__(self)
        self.publish = publish

    def run(self):
        global rcv_cnt
        global start
        r = redis.Redis(host='localhost', port=6379, db=0)
        p = r.pubsub(ignore_subscribe_messages=True)
        if self.publish:
            p.psubscribe('ticker*')
        else:
            p.psubscribe('__keyspace*__:*')

        for m in p.listen():
            #print(m)
            if not self.publish:
                k = m['channel'].decode('utf-8').split(':')[1]
                #print("key is {}".format(k))
                m = r.hgetall(k)
                print(m)
            if start is None:
                start = timer()

            rcv_cnt += 1
            if rcv_cnt >= tot:
                end = timer()
                print(end - start)

                # get values of all keys
                start = timer()
                keys = r.keys('ticker*')
                pipe = r. pipeline()
                for k in keys:
                    pipe.hgetall(k.decode('utf-8'))
                pipe.execute()
                print(timer() - start)

                break


if __name__ == "__main__":
    from docopt import docopt
    from timeit import default_timer as timer
    args = docopt(__doc__)

    tot = int(args['-n'])

    t = myThread(args['-p'])
    t.start()
    t.join()
