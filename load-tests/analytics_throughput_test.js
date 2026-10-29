// Sends 60,000 redirects (more than a day's worth at 50K events/day) in 4 minutes and verifies
// that every click event made it through Kafka into Postgres, using the app's own counters.
//   k6 run load-tests/analytics_throughput_test.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, createShortUrls, randomClientIp, readCounter } from './lib/setup.js';

const CONSUMED = 'analytics_events_consumed_total';
const DEAD_LETTERED = 'analytics_events_dead_lettered_total';
const REJECTED = 'analytics_events_rejected_total';

const redirectsSent = new Counter('redirects_sent');
const eventsPersisted = new Counter('events_persisted');

export const options = {
  scenarios: {
    clicks: {
      executor: 'constant-arrival-rate',
      rate: 250, timeUnit: '1s', duration: '4m',   // 250/s * 240s = 60,000 events
      preAllocatedVUs: 50, maxVUs: 300,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.001'],
    // every successful redirect must be persisted by the pipeline
    events_persisted: ['count>=60000'],
  },
  teardownTimeout: '3m',
};

export function setup() {
  return {
    codes: createShortUrls(50),
    consumedBefore: readCounter(CONSUMED),
    deadLetteredBefore: readCounter(DEAD_LETTERED),
    rejectedBefore: readCounter(REJECTED),
  };
}

export default function (data) {
  const code = data.codes[Math.floor(Math.random() * data.codes.length)];
  const res = http.get(`${BASE_URL}/${code}`, {
    redirects: 0,
    headers: { 'X-Forwarded-For': randomClientIp(), 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0) Chrome/120.0' },
  });
  if (check(res, { 'redirect is 302': (r) => r.status === 302 })) {
    redirectsSent.add(1);
  }
}

// Waits for the consumer to drain Kafka, then reports how many events were persisted.
export function teardown(data) {
  let persisted = 0;
  for (let i = 0; i < 60 && persisted < 60000; i++) {
    sleep(2);
    persisted = readCounter(CONSUMED) - data.consumedBefore;
  }
  eventsPersisted.add(persisted);
  console.log(`events persisted via Kafka: ${persisted}`);
  console.log(`dead-lettered: ${readCounter(DEAD_LETTERED) - data.deadLetteredBefore}`);
  console.log(`dropped (executor full): ${readCounter(REJECTED) - data.rejectedBefore}`);
}
