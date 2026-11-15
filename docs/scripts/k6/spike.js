import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.TARGET_URL;
const SHIPMENT_ID = Number(__ENV.SHIPMENT_ID);
const URL = `${BASE_URL}/tracking/shipments/${SHIPMENT_ID}`;

export const options = {
    stages: [
        { duration: '10s', target: 10 },
        { duration: '5s', target: 80 },
        { duration: '20s', target: 80 },
        { duration: '5s', target: 10 },
        { duration: '10s', target: 0 }
    ],
    thresholds: {
        http_req_duration: ['p(95)<1000'],
        http_req_failed: ['rate<0.01'],
        checks: ['rate>0.99']
    }
};

export default function spikeTest() {
    const response = http.get(URL);
    const shipment =
        response.status === 200 ? response.json() : null;

    check(response, {
        'shipment available': () =>
            response.status === 200 &&
            shipment.shipmentId === SHIPMENT_ID &&
            typeof shipment.status === 'string' &&
            shipment.status !== 'UNAVAILABLE'
    });

    sleep(1);
}