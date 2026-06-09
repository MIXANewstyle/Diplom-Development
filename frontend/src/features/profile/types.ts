export type GenderId = 1 | 2 | 3;

export interface MyProfile {
  id: string;
  email: string;
  role: string;
  username: string;
  fullName: string;
  bio: string | null;
  avatarUrl: string | null;
  contactInfo: string | null;
  birthDate: string | null;
  genderId: GenderId | null;
  psychProfile: string | null;
  updatedAt: string;
}

export type ProfileUpdateBody = Partial<{
  fullName: string | null;
  username: string | null;
  bio: string | null;
  avatarUrl: string | null;
  contactInfo: string | null;
  birthDate: string | null;
  genderId: GenderId | null;
  psychProfile: string | null;
}>;

export interface PsychProfile {
  about_self?: string;
  reason?: string;
  goals?: string;
  prior_experience?: '' | 'none' | 'some' | 'extensive';
}

export const GENDER_OPTIONS: Record<number, string> = {
  1: 'Мужской',
  2: 'Женский',
  3: 'Другое',
};
