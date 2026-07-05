export type TrailDifficulty = 'easy' | 'moderate' | 'hard';
export type Season = 'spring' | 'summer' | 'autumn' | 'winter';

export interface Trail {
  id: string;
  name: string;
  region: string;
  typicalDays: number;
  difficulty: TrailDifficulty;
  distanceKm: number;
  highlights: string[];
}

export type TransportMode = 'high-speed-rail' | 'bus';

export interface TransportOption {
  id: string;
  trailId: string;
  from: string;
  mode: TransportMode;
  departure: string;
  durationMinutes: number;
  // One-way price per person, in CNY cents.
  priceCents: number;
}

export type LodgingTier = 'budget' | 'comfort' | 'boutique';

export interface LodgingOption {
  id: string;
  trailId: string;
  name: string;
  tier: LodgingTier;
  // Per room per night, in CNY cents.
  pricePerNightCents: number;
  distanceToTrailheadKm: number;
}

export const mockTrails: Record<string, Trail> = {
  trail_moganshan: {
    id: 'trail_moganshan',
    name: '莫干山环线',
    region: '浙江湖州',
    typicalDays: 2,
    difficulty: 'easy',
    distanceKm: 16,
    highlights: ['竹海', '民国别墅群', '山顶日出'],
  },
  trail_huihang: {
    id: 'trail_huihang',
    name: '徽杭古道',
    region: '安徽绩溪—浙江临安',
    typicalDays: 2,
    difficulty: 'moderate',
    distanceKm: 25,
    highlights: ['江南第一关', '蓝天凹草甸', '古道石板路'],
  },
  trail_siming: {
    id: 'trail_siming',
    name: '四明山红枫线',
    region: '浙江余姚',
    typicalDays: 1,
    difficulty: 'moderate',
    distanceKm: 18,
    highlights: ['红枫林', '高山梯田', '溪谷穿越'],
  },
};

export const mockTransportOptions: TransportOption[] = [
  {
    id: 'tr_mgs_rail_am',
    trailId: 'trail_moganshan',
    from: '上海',
    mode: 'high-speed-rail',
    departure: '08:15',
    durationMinutes: 75,
    priceCents: 9200,
  },
  {
    id: 'tr_mgs_bus_am',
    trailId: 'trail_moganshan',
    from: '上海',
    mode: 'bus',
    departure: '07:30',
    durationMinutes: 170,
    priceCents: 6500,
  },
  {
    id: 'tr_hh_rail_am',
    trailId: 'trail_huihang',
    from: '上海',
    mode: 'high-speed-rail',
    departure: '07:02',
    durationMinutes: 150,
    priceCents: 15600,
  },
  {
    id: 'tr_hh_bus_am',
    trailId: 'trail_huihang',
    from: '杭州',
    mode: 'bus',
    departure: '06:40',
    durationMinutes: 160,
    priceCents: 8800,
  },
  {
    id: 'tr_sm_rail_am',
    trailId: 'trail_siming',
    from: '上海',
    mode: 'high-speed-rail',
    departure: '07:26',
    durationMinutes: 110,
    priceCents: 11400,
  },
];

export const mockLodgingOptions: LodgingOption[] = [
  {
    id: 'lg_mgs_hostel',
    trailId: 'trail_moganshan',
    name: '莫干山青年旅舍',
    tier: 'budget',
    pricePerNightCents: 12000,
    distanceToTrailheadKm: 1.2,
  },
  {
    id: 'lg_mgs_guesthouse',
    trailId: 'trail_moganshan',
    name: '竹里馆民宿',
    tier: 'comfort',
    pricePerNightCents: 42000,
    distanceToTrailheadKm: 0.6,
  },
  {
    id: 'lg_mgs_resort',
    trailId: 'trail_moganshan',
    name: '山顶精品度假村',
    tier: 'boutique',
    pricePerNightCents: 180000,
    distanceToTrailheadKm: 2.5,
  },
  {
    id: 'lg_hh_farmstay',
    trailId: 'trail_huihang',
    name: '江南第一关农家乐',
    tier: 'budget',
    pricePerNightCents: 9000,
    distanceToTrailheadKm: 0.3,
  },
  {
    id: 'lg_hh_inn',
    trailId: 'trail_huihang',
    name: '蓝天凹客栈',
    tier: 'comfort',
    pricePerNightCents: 26000,
    distanceToTrailheadKm: 0,
  },
  {
    id: 'lg_sm_guesthouse',
    trailId: 'trail_siming',
    name: '四明山居民宿',
    tier: 'comfort',
    pricePerNightCents: 30000,
    distanceToTrailheadKm: 1.8,
  },
];

export interface TrailQuery {
  keyword?: string;
  maxDays?: number;
  difficulty?: TrailDifficulty;
}

export function searchTrails(query: TrailQuery = {}): Trail[] {
  return Object.values(mockTrails).filter((trail) => {
    if (query.maxDays !== undefined && trail.typicalDays > query.maxDays) {
      return false;
    }
    if (query.difficulty && trail.difficulty !== query.difficulty) {
      return false;
    }
    if (query.keyword) {
      const haystack = [trail.id, trail.name, trail.region, ...trail.highlights].join(' ');
      if (!haystack.includes(query.keyword)) {
        return false;
      }
    }
    return true;
  });
}

export function getTrail(trailId: string): Trail {
  const trail = mockTrails[trailId];
  if (!trail) {
    throw new Error(`Trail not found: ${trailId}`);
  }

  return trail;
}

export function searchTransport(from: string, trailId: string): TransportOption[] {
  getTrail(trailId);

  return mockTransportOptions
    .filter((option) => option.trailId === trailId && option.from === from)
    .sort((a, b) => a.priceCents - b.priceCents);
}

export function getTransportOption(transportId: string): TransportOption {
  const option = mockTransportOptions.find((candidate) => candidate.id === transportId);
  if (!option) {
    throw new Error(`Transport option not found: ${transportId}`);
  }

  return option;
}

export interface LodgingSearchResult {
  options: LodgingOption[];
  reason?: string;
}

export function searchLodging(
  trailId: string,
  maxPricePerNightCents?: number,
): LodgingSearchResult {
  getTrail(trailId);

  const all = mockLodgingOptions
    .filter((option) => option.trailId === trailId)
    .sort((a, b) => a.pricePerNightCents - b.pricePerNightCents);

  if (maxPricePerNightCents === undefined) {
    return { options: all };
  }

  const within = all.filter(
    (option) => option.pricePerNightCents <= maxPricePerNightCents,
  );
  if (within.length === 0) {
    const cheapest = all[0];
    return {
      options: [],
      reason: `No lodging at or below ${formatYuan(maxPricePerNightCents)} per night; the cheapest option is ${cheapest.name} at ${formatYuan(cheapest.pricePerNightCents)} per night.`,
    };
  }

  return { options: within };
}

export function getLodgingOption(lodgingId: string): LodgingOption {
  const option = mockLodgingOptions.find((candidate) => candidate.id === lodgingId);
  if (!option) {
    throw new Error(`Lodging option not found: ${lodgingId}`);
  }

  return option;
}

const basePackingList = [
  '登山鞋',
  '速干衣',
  '雨具',
  '头灯',
  '1.5L 以上饮水',
  '行动粮',
  '个人药品',
];

const difficultyPackingExtras: Record<TrailDifficulty, string[]> = {
  easy: [],
  moderate: ['登山杖', '护膝'],
  hard: ['登山杖', '护膝', '备用衣物', '急救毯'],
};

const seasonPackingExtras: Record<Season, string[]> = {
  spring: ['防风外套', '防蚊液'],
  summer: ['防晒霜', '遮阳帽', '电解质补给'],
  autumn: ['抓绒衣'],
  winter: ['羽绒服', '保温壶', '简易冰爪'],
};

export function buildPackingList(trailId: string, season: Season = 'autumn'): string[] {
  const trail = getTrail(trailId);

  return [
    ...basePackingList,
    ...difficultyPackingExtras[trail.difficulty],
    ...seasonPackingExtras[season],
  ];
}

export interface ItineraryDayPlan {
  day: number;
  title: string;
  activities: string[];
}

export interface ItineraryPlan {
  trail: Trail;
  days: ItineraryDayPlan[];
  transportCents: number;
  lodgingCents: number;
  totalBudgetCents: number;
  summary: string;
}

export interface BuildItineraryParams {
  trailId: string;
  days: number;
  transport: TransportOption;
  lodging?: LodgingOption;
  partySize?: number;
}

export function formatYuan(cents: number): string {
  const yuan = cents / 100;
  return `¥${Number.isInteger(yuan) ? yuan : yuan.toFixed(2)}`;
}

const transportModeLabels: Record<TransportMode, string> = {
  'high-speed-rail': '高铁',
  bus: '大巴',
};

export function buildItinerary(params: BuildItineraryParams): ItineraryPlan {
  const trail = getTrail(params.trailId);

  if (!Number.isInteger(params.days) || params.days < 1 || params.days > 5) {
    throw new Error(`Unsupported trip length: ${params.days} days`);
  }

  const nights = params.days - 1;
  if (nights > 0 && !params.lodging) {
    throw new Error('Lodging is required for trips with overnight stays.');
  }

  const partySize = params.partySize ?? 1;
  const transportLabel = transportModeLabels[params.transport.mode];
  const arrivalActivities = [
    `${params.transport.departure} 从${params.transport.from}乘${transportLabel}出发（约 ${params.transport.durationMinutes} 分钟）`,
  ];
  if (params.lodging) {
    arrivalActivities.push(`抵达后入住${params.lodging.name}，寄存大件行李`);
  }

  const days: ItineraryDayPlan[] = [];

  if (params.days === 1) {
    days.push({
      day: 1,
      title: '一日往返徒步',
      activities: [
        ...arrivalActivities,
        `完成${trail.name}全程约 ${trail.distanceKm} km，途经${trail.highlights.join('、')}`,
        '傍晚乘车返程',
      ],
    });
  } else {
    days.push({
      day: 1,
      title: '出发与适应性徒步',
      activities: [
        ...arrivalActivities,
        `下午轻装适应性徒步，先睹${trail.highlights[0]}`,
      ],
    });
    for (let day = 2; day < params.days; day += 1) {
      days.push({
        day,
        title: '主线徒步',
        activities: [
          `徒步${trail.name}主线段，途经${trail.highlights.join('、')}`,
          '晚上返回住宿地休整',
        ],
      });
    }
    days.push({
      day: params.days,
      title: '收尾与返程',
      activities: [
        `完成剩余路段，合计全程约 ${trail.distanceKm} km`,
        '午后乘车返程',
      ],
    });
  }

  const transportCents = params.transport.priceCents * 2 * partySize;
  const lodgingCents = (params.lodging?.pricePerNightCents ?? 0) * nights;
  const totalBudgetCents = transportCents + lodgingCents;

  const summaryParts = [
    `${trail.name}（${trail.region}）${params.days} 天行程`,
    `往返交通 ${formatYuan(transportCents)}（${partySize} 人）`,
  ];
  if (params.lodging && nights > 0) {
    summaryParts.push(`住宿${params.lodging.name} ${nights} 晚 ${formatYuan(lodgingCents)}`);
  }
  summaryParts.push(`合计约 ${formatYuan(totalBudgetCents)}`);

  return {
    trail,
    days,
    transportCents,
    lodgingCents,
    totalBudgetCents,
    summary: summaryParts.join('，') + '。',
  };
}
