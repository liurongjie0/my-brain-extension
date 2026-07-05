import { describe, expect, it } from 'vitest';
import {
  buildItinerary,
  buildPackingList,
  getLodgingOption,
  getTrail,
  getTransportOption,
  mockLodgingOptions,
  searchLodging,
  searchTrails,
  searchTransport,
} from './travel.ts';

describe('searchTrails', () => {
  it('returns all trails without a query', () => {
    expect(searchTrails()).toHaveLength(3);
  });

  it('filters by keyword, max days, and difficulty', () => {
    expect(searchTrails({ keyword: '莫干山' }).map((trail) => trail.id)).toEqual([
      'trail_moganshan',
    ]);
    expect(searchTrails({ maxDays: 1 }).map((trail) => trail.id)).toEqual([
      'trail_siming',
    ]);
    expect(
      searchTrails({ difficulty: 'moderate' }).map((trail) => trail.id),
    ).toEqual(['trail_huihang', 'trail_siming']);
  });
});

describe('getTrail', () => {
  it('throws for unknown trails', () => {
    expect(() => getTrail('trail_unknown')).toThrow('Trail not found');
  });
});

describe('searchTransport', () => {
  it('returns options for a departure city sorted by price', () => {
    const options = searchTransport('上海', 'trail_moganshan');
    expect(options.map((option) => option.id)).toEqual([
      'tr_mgs_bus_am',
      'tr_mgs_rail_am',
    ]);
  });

  it('returns an empty list for a city with no options', () => {
    expect(searchTransport('北京', 'trail_moganshan')).toEqual([]);
  });

  it('throws for unknown trails', () => {
    expect(() => searchTransport('上海', 'trail_unknown')).toThrow('Trail not found');
  });
});

describe('searchLodging', () => {
  it('returns all lodging sorted by price without a budget', () => {
    const result = searchLodging('trail_moganshan');
    expect(result.options.map((option) => option.id)).toEqual([
      'lg_mgs_hostel',
      'lg_mgs_guesthouse',
      'lg_mgs_resort',
    ]);
    expect(result.reason).toBeUndefined();
  });

  it('filters by max price per night', () => {
    const result = searchLodging('trail_moganshan', 15000);
    expect(result.options.map((option) => option.id)).toEqual(['lg_mgs_hostel']);
  });

  it('explains why nothing matches an impossible budget', () => {
    const result = searchLodging('trail_moganshan', 5000);
    expect(result.options).toEqual([]);
    expect(result.reason).toContain('莫干山青年旅舍');
  });
});

describe('option lookups', () => {
  it('resolves transport and lodging by ID and throws otherwise', () => {
    expect(getTransportOption('tr_mgs_rail_am').mode).toBe('high-speed-rail');
    expect(getLodgingOption('lg_hh_inn').tier).toBe('comfort');
    expect(() => getTransportOption('tr_nope')).toThrow('Transport option not found');
    expect(() => getLodgingOption('lg_nope')).toThrow('Lodging option not found');
  });
});

describe('buildPackingList', () => {
  it('adds difficulty and season extras on top of the base list', () => {
    const easyAutumn = buildPackingList('trail_moganshan');
    expect(easyAutumn).toContain('登山鞋');
    expect(easyAutumn).toContain('抓绒衣');
    expect(easyAutumn).not.toContain('登山杖');

    const moderateWinter = buildPackingList('trail_huihang', 'winter');
    expect(moderateWinter).toContain('登山杖');
    expect(moderateWinter).toContain('羽绒服');
  });
});

describe('buildItinerary', () => {
  const transport = getTransportOption('tr_mgs_rail_am');
  const lodging = getLodgingOption('lg_mgs_hostel');

  it('builds a two-day plan with a budget summary', () => {
    const plan = buildItinerary({
      trailId: 'trail_moganshan',
      days: 2,
      transport,
      lodging,
      partySize: 2,
    });

    expect(plan.days.map((day) => day.day)).toEqual([1, 2]);
    expect(plan.transportCents).toBe(9200 * 2 * 2);
    expect(plan.lodgingCents).toBe(12000);
    expect(plan.totalBudgetCents).toBe(9200 * 2 * 2 + 12000);
    expect(plan.summary).toContain('莫干山环线');
    expect(plan.summary).toContain('¥');
  });

  it('builds a single-day plan without lodging', () => {
    const plan = buildItinerary({
      trailId: 'trail_siming',
      days: 1,
      transport: getTransportOption('tr_sm_rail_am'),
    });

    expect(plan.days).toHaveLength(1);
    expect(plan.lodgingCents).toBe(0);
  });

  it('rejects overnight trips without lodging and bad day counts', () => {
    expect(() =>
      buildItinerary({ trailId: 'trail_moganshan', days: 2, transport }),
    ).toThrow('Lodging is required');
    expect(() =>
      buildItinerary({ trailId: 'trail_moganshan', days: 0, transport }),
    ).toThrow('Unsupported trip length');
  });
});

describe('mock data integrity', () => {
  it('keeps every lodging option attached to a known trail', () => {
    for (const option of mockLodgingOptions) {
      expect(() => getTrail(option.trailId)).not.toThrow();
    }
  });
});
