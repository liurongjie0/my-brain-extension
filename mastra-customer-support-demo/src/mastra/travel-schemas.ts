import { z } from 'zod';

export const trailDifficultySchema = z.enum(['easy', 'moderate', 'hard']);

export const seasonSchema = z.enum(['spring', 'summer', 'autumn', 'winter']);

export const trailSchema = z.object({
  id: z.string(),
  name: z.string(),
  region: z.string(),
  typicalDays: z.number().int().positive(),
  difficulty: trailDifficultySchema,
  distanceKm: z.number().positive(),
  highlights: z.array(z.string()),
});

export const transportOptionSchema = z.object({
  id: z.string(),
  trailId: z.string(),
  from: z.string(),
  mode: z.enum(['high-speed-rail', 'bus']),
  departure: z.string(),
  durationMinutes: z.number().int().positive(),
  priceCents: z.number().int().nonnegative(),
});

export const lodgingOptionSchema = z.object({
  id: z.string(),
  trailId: z.string(),
  name: z.string(),
  tier: z.enum(['budget', 'comfort', 'boutique']),
  pricePerNightCents: z.number().int().nonnegative(),
  distanceToTrailheadKm: z.number().nonnegative(),
});

export const lodgingSearchResultSchema = z.object({
  options: z.array(lodgingOptionSchema),
  reason: z.string().optional(),
});

export const itineraryDayPlanSchema = z.object({
  day: z.number().int().positive(),
  title: z.string(),
  activities: z.array(z.string()),
});

export const itineraryPlanSchema = z.object({
  trail: trailSchema,
  days: z.array(itineraryDayPlanSchema),
  transportCents: z.number().int().nonnegative(),
  lodgingCents: z.number().int().nonnegative(),
  totalBudgetCents: z.number().int().nonnegative(),
  summary: z.string(),
});
