import { WorldEntity } from './models';

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : undefined;
}

export function entityFile(entity: WorldEntity, fileName: string): Record<string, unknown> | undefined {
  const files = asRecord(entity.files);
  if (!files) {
    return undefined;
  }
  return asRecord(files[fileName]) ?? asRecord(files[fileName.replace(/\.json$/, '')]);
}

function stringList(value: unknown): string[] {
  if (typeof value === 'string' && value.trim()) {
    return value.split(',').map((part) => part.trim()).filter(Boolean);
  }
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter((item): item is string => typeof item === 'string' && item.trim().length > 0);
}

export function traitsOf(entity: WorldEntity): string[] {
  return stringList(entityFile(entity, 'personality.json')?.['traits']).slice(0, 5);
}

export function appearanceOf(entity: WorldEntity): string {
  const appearance = entityFile(entity, 'appearance.json');
  const features = asRecord(appearance?.['physicalFeatures']);
  const parts = [features?.['hair'], features?.['eyes']]
    .filter((item): item is string => typeof item === 'string' && item.trim().length > 0);
  return parts.join(' · ');
}

export function strengthsOf(entity: WorldEntity): string[] {
  return stringList(entityFile(entity, 'stats.json')?.['strengths']).slice(0, 3);
}

export function metaLine(entity: WorldEntity): string {
  return [entity.class, entity.rank ? `rank ${entity.rank}` : '', entity.location]
    .filter((part): part is string => Boolean(part && part.trim()))
    .join(' · ');
}
