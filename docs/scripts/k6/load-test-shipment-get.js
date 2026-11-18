// Load test: normal load on GET /shipments/{id} (the call tracking-service makes to shipment-service).
// Run: k6 run -e TARGET_URL=http://localhost:8092 docs/scripts/k6/load-test-shipment-get.js
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 20 }, // ramp-up
    { duration: '2m', target: 20 },  // hold
    { duration: '30s', target: 0 },  // ramp-down
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost:8082';
const SEED_COUNT = Number(__ENV.SEED_COUNT || 20);
const JSON_HEADERS = { 'Content-Type': 'application/json' };

// Creates test shipments (quote -> shipment) so that GET has real ids to read.
export function setup() {
  const ids = [];
  for (let i = 0; i < SEED_COUNT; i++) {
    const quote = http.post(`${BASE_URL}/shipments/quotes`, JSON.stringify({
      pickupAddress: 'Khreshchatyk 1',
      pickupCityCode: 'UA80000000000093317',
      deliveryAddress: 'Rynok 1',
      deliveryCityCode: 'UA46060000000043258',
      weight: 2.5,
    }), { headers: { ...JSON_HEADERS, 'X-User-Id': '1' } });
    const res = http.post(`${BASE_URL}/shipments`, JSON.stringify({
      quoteId: quote.json('quoteId'),
      senderName: 'Load Sender',
      senderPhone: '+380501112233',
      recipientName: `Recipient ${i}`,
      recipientPhone: '+380671112233',
      length: 20, width: 15, height: 10,
    }), { headers: { ...JSON_HEADERS, 'X-User-Id': '1', 'Idempotency-Key': `k6-seed-${Date.now()}-${i}` } });
    if (res.status === 200) ids.push(res.json('shipmentId'));
  }
  if (ids.length === 0) throw new Error('could not seed any shipments');
  return { ids };
}

export default function (data) {
  const id = data.ids[Math.floor(Math.random() * data.ids.length)];
  const res = http.get(`${BASE_URL}/shipments/${id}`, { tags: { name: 'GET /shipments/{id}' } });
  check(res, {
    'status 200': (r) => r.status === 200,
    'has shipmentId, recipientName, status': (r) => {
      const b = r.json();
      return b.shipmentId !== undefined && !!b.recipientName && !!b.status;
    },
  });
  sleep(0.5);
}
