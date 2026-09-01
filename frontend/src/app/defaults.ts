export type EntityClass = 'mage' | 'melee';

export interface EntityDraft {
  name: string;
  class: EntityClass;
  rank: string;
  gender: string;
  age: string;
  location: string;
  description: string;
  openingMessage: string;
  traits: string;
  likes: string;
  dislikes: string;
  speechTone: string;
  speechStyle: string;
  speechVerbosity: string;
  heightCm: number;
  weightKg: number;
  build: string;
  hair: string;
  eyes: string;
  skin: string;
  face: string;
  vigor: number;
  mind: number;
  endurance: number;
  strength: number;
  dexterity: number;
  intelligence: number;
  faith: number;
  arcane: number;
  strengths: string;
  weaknesses: string;
  weapons: string;
  armor: string;
  clothingDefault: string;
  clothingCombat: string;
  clothingFormal: string;
}

function descriptionFor(name: string, characterClass: EntityClass): string {
  const who = name.trim() || 'They';
  if (characterClass === 'mage') {
    return `${who} is a newly created mage. They fight with intelligence and arcane power, starting at rank E with a training staff and little physical strength.`;
  }
  return `${who} is a newly created melee fighter. They fight up close with strength and endurance, starting at rank E with a training blade and little magical ability.`;
}

export function createDraft(name = '', characterClass: EntityClass = 'mage'): EntityDraft {
  const mage = characterClass === 'mage';
  const label = name.trim() || 'Newcomer';
  return {
    name,
    class: characterClass,
    rank: 'E',
    gender: '',
    age: '',
    location: 'guildhall',
    description: descriptionFor(label, characterClass),
    openingMessage: `${label} turns toward you and waits for you to speak.`,
    traits: mage ? 'focused, curious' : 'disciplined, direct',
    likes: '',
    dislikes: '',
    speechTone: mage ? 'measured' : 'plain',
    speechStyle: mage ? 'precise' : 'blunt',
    speechVerbosity: mage ? 'moderate' : 'low',
    heightCm: 0,
    weightKg: 0,
    build: '',
    hair: '',
    eyes: '',
    skin: '',
    face: '',
    vigor: mage ? 40 : 70,
    mind: mage ? 70 : 35,
    endurance: mage ? 40 : 70,
    strength: mage ? 25 : 75,
    dexterity: mage ? 45 : 55,
    intelligence: mage ? 75 : 30,
    faith: mage ? 50 : 35,
    arcane: mage ? 70 : 20,
    strengths: mage ? 'Magical aptitude, Tactical awareness' : 'Physical power, Close-quarters combat',
    weaknesses: mage
      ? 'Low physical strength, Limited close-quarters durability'
      : 'Limited ranged magic, Lower arcane resistance',
    weapons: mage ? 'training staff' : 'training blade',
    armor: mage ? '' : 'light training gear',
    clothingDefault: mage ? 'plain robes' : 'simple tunic',
    clothingCombat: mage ? 'battle robes' : 'padded combat gear',
    clothingFormal: mage ? 'formal robes' : 'formal tunic'
  };
}

export function applyClassDefaults(draft: EntityDraft, characterClass: EntityClass): EntityDraft {
  const next = createDraft(draft.name, characterClass);
  next.gender = draft.gender;
  next.age = draft.age;
  next.heightCm = draft.heightCm;
  next.weightKg = draft.weightKg;
  next.build = draft.build;
  next.hair = draft.hair;
  next.eyes = draft.eyes;
  next.skin = draft.skin;
  next.face = draft.face;
  return next;
}

function csv(value: string): string[] {
  return value
    .split(',')
    .map((part) => part.trim())
    .filter((part) => part.length > 0);
}

export function toUpdateBody(draft: EntityDraft): Record<string, unknown> {
  return {
    class: draft.class,
    rank: draft.rank,
    gender: draft.gender,
    age: draft.age,
    location: draft.location,
    description: draft.description,
    openingMessage: draft.openingMessage,
    personality: {
      traits: csv(draft.traits),
      likes: csv(draft.likes),
      dislikes: csv(draft.dislikes),
      speech: {
        tone: draft.speechTone,
        style: draft.speechStyle,
        verbosity: draft.speechVerbosity
      }
    },
    appearance: {
      body: {
        heightCm: Number(draft.heightCm) || 0,
        weightKg: Number(draft.weightKg) || 0,
        build: draft.build
      },
      physicalFeatures: {
        hair: draft.hair,
        eyes: draft.eyes,
        skin: draft.skin,
        face: draft.face
      }
    },
    stats: {
      attributes: {
        vigor: Number(draft.vigor) || 0,
        mind: Number(draft.mind) || 0,
        endurance: Number(draft.endurance) || 0,
        strength: Number(draft.strength) || 0,
        dexterity: Number(draft.dexterity) || 0,
        intelligence: Number(draft.intelligence) || 0,
        faith: Number(draft.faith) || 0,
        arcane: Number(draft.arcane) || 0
      },
      strengths: csv(draft.strengths),
      weaknesses: csv(draft.weaknesses)
    },
    equipment: {
      weapons: csv(draft.weapons),
      armor: csv(draft.armor),
      clothing: {
        default: draft.clothingDefault,
        combat: draft.clothingCombat,
        formal: draft.clothingFormal
      }
    }
  };
}
