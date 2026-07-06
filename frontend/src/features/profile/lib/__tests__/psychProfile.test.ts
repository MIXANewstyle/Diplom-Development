import { describe, it, expect } from 'vitest';
import { psychProfileLength, psychProfileUserLength } from '../psychProfile';

describe('psychProfileLength', () => {
  it('counts serialized JSON length', () => {
    expect(psychProfileLength({})).toBe(2); // "{}"
  });
});

describe('psychProfileUserLength', () => {
  it('returns 0 for empty form', () => {
    expect(psychProfileUserLength({})).toBe(0);
  });

  it('counts exactly typed characters in one field', () => {
    expect(psychProfileUserLength({ about_self: 'a' })).toBe(1);
    expect(psychProfileUserLength({ about_self: 'abc' })).toBe(3);
  });

  it('ignores whitespace-only fields', () => {
    expect(psychProfileUserLength({ about_self: '   ', reason: ' \n ' })).toBe(0);
  });

  it('sums across all fields', () => {
    expect(psychProfileUserLength({
      about_self: '123',
      reason: '456',
      goals: '789',
      prior_experience: 'none'
    })).toBe(3 + 3 + 3 + 4);
  });
});
