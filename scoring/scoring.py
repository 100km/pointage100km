#! /usr/bin/python2
#

import couchdb, sys

starts = {1: 1368032400000, 2: 1368072000000, 3: 1368032400000, 5: 1368072000000}

offsets = {0: 6570,
           1: 11600,
           3: 4160,
           4: 6770,
           5: 11630,
           'l': 0}

server = couchdb.client.Server()
db = server['steenwerck100km']

sys.stderr.write("This software output SQL statement to put in your database ! Don't forget to change the race_id to the one you have inserted for the new year ! And put a semi colon at the end instead of the coma.\n")

class Contestant:

    def __init__(self, bib, first_name, name, sex, birth, race_id, city, zipcode):
        self.bib = bib
        self.first_name = first_name
        self.name = name
        self.sex = sex
        self.is_woman = self.sex in ['f', "F"]
        self.birth = birth
        self.race_id = race_id
        self.city = city
        self.zipcode = zipcode
        self.checkpoints = []

    def category(self):
        age = 2013 - int(self.birth[:4])
        if age <= 9:
            return "eveil"
        elif age <= 11:
            return "poussin"
        elif age <= 13:
            return "benjamin"
        elif age <= 15:
            return "minime"
        elif age <= 17:
            return "cadet"
        elif age <= 19:
            return "junior"
        elif age <= 22:
            return "espoir"
        elif age <= 39:
            return "senior"
        elif age <= 49:
            return "veteran 1"
        elif age <= 59:
            return "veteran 2"
        elif age <= 69 or self.is_woman:
            return "veteran 3"
        else:
            return "veteran 4"

    def race_time(self):
        if self.checkpoints:
            return round((self.end_time - starts[self.race_id]) / 1000)

    def race_hms(self):
        if self.checkpoints:
            t = self.race_time()
            return (t // 3600, (t // 60) % 60, t % 60)

    def distance(self):
        if self.checkpoints:
            return 5680 + 15720 * self.loops + self.offset

    def set_checkpoints(self, checkpoints):
        self.checkpoints = checkpoints
        if not checkpoints:
            return
        n = []
        p = False   # p: previous was a loop end
        m = False   # m: None if checkpoint not in second-half of loop, checkpoint otherwise
        for (t, s) in checkpoints:
            if s in [2, 6]:
                # All loops are equal, but do not enter two loops at once
                if not p:
                    n.append((t, 'l'))
                p = True
            else:
                # If we go from second part of loop to first part of loop, add a loop
                if m is not None and s in [0, 3] and s != m:
                    n.append((t, 'l'))
                    p = True
                n.append((t, s))
                p = False
            m = s if s in [1, 4, 5] else None
        self.loops = map(lambda x: x[1], n).count('l')
        self.offset = offsets[n[-1][1]]
        self.end_time = n[-1][0]

contestants = []
all_res = []

for bib in range(0, 1000):
    docid = 'contestant-%d' % bib
    if docid in db:
        doc = db[docid]
        race_id = doc['race']
        if race_id not in starts:
            continue
        contestant = Contestant(bib = doc['bib'],
                                first_name = doc['first_name'],
                                name = doc['name'],
                                sex = doc['sex'],
                                race_id = race_id,
                                birth = doc['birth'],
                                city = doc['city'],
                                zipcode = doc['zipcode'])
        checkpoints = []
        for checkpoint in range(0, 7):
            cpid = 'checkpoints-%d-%d' % (checkpoint, bib)
            if cpid not in db:
                continue
            cp = db[cpid]
            site_id = cp['site_id']
            deleted_times = cp['deleted_times'] if 'deleted_times' in cp else []
            artificial_times = cp['artificial_times'] if 'artificial_times' in cp else []
            for t in cp['times']:
                if t not in deleted_times and t not in artificial_times:
                    checkpoints.append((t, site_id))
        if not checkpoints:
            continue
        contestants.append(contestant)
        checkpoints.sort()
        contestant.set_checkpoints(checkpoints)
        distance = contestant.distance()
        if distance > 100000:
            sys.stderr.write("Distance for bib %d is %s: %s\n" % (contestant.bib, distance, contestant.checkpoints))
            distance = 100000
        hours, minutes, seconds = contestant.race_hms()
        all_res.append((
            contestant.name,
            contestant.first_name,
            contestant.bib,
            contestant.race_id,
            contestant.sex,
            distance,
            contestant.race_time(),
            hours,
            minutes,
            seconds,
            contestant.city,
            contestant.zipcode,
            contestant.category()
        ))
        s = "%s,%s,%d,%d,%s,%s,%d,%d:%02d:%02d,%s,%s,%s" % (
            contestant.name,
            contestant.first_name,
            contestant.bib,
            contestant.race_id,
            contestant.sex,
            distance,
            contestant.race_time(),
            hours,
            minutes,
            seconds,
            contestant.city,
            contestant.zipcode,
            contestant.category())
#        sys.stdout.write("%s\n" % s.encode("utf-8"))
#        sys.stdout.flush()


all_res = sorted(all_res, key=lambda tup: [tup[3], -tup[5], tup[6]])

#print
#print

cur_race = 0
sys.stdout.write("INSERT INTO race_rankings (race_id, bib, position, distance, time) VALUES\n")
for t in all_res:
  if (cur_race != t[3]):
    position = 0
    cur_race = t[3]

  position = position + 1
  s = "(%d,%d,%d,%s,'%d:%02d:%02d')," % (t[3], t[2], position, t[5], t[7], t[8], t[9])
  sys.stdout.write("%s\n" % s.encode("utf-8"))
  sys.stdout.flush()
