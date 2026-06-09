import type { PsychProfile } from '../types';

export function parsePsychProfile(raw: string | null): PsychProfile {
  if (!raw) return {};
  try {
    const parsed = JSON.parse(raw);
    if (typeof parsed === 'object' && parsed !== null) {
      return parsed as PsychProfile;
    }
  } catch (e) {
    // ignore parsing errors
  }
  return {};
}

export function serializePsychProfile(p: PsychProfile): string {
  const cleaned: any = {};
  if (p.about_self?.trim()) cleaned.about_self = p.about_self.trim();
  if (p.reason?.trim()) cleaned.reason = p.reason.trim();
  if (p.goals?.trim()) cleaned.goals = p.goals.trim();
  if (p.prior_experience) cleaned.prior_experience = p.prior_experience;
  
  return JSON.stringify(cleaned);
}

export function psychProfileLength(p: PsychProfile): number {
  return serializePsychProfile(p).length;
}
