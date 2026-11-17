// The study planner's state and actions, shared by its views. The plan itself lives in the app
// state (the chat and the start page use it too); this holds the planner's own controls.
import { createContext, useContext } from 'react';
import type { Plan, ProgrammeInfo } from '../lib/types';

export type View = 'board' | 'step' | 'week';
export interface PlanForm { focus: string; start: string; semesters: number; max: number | null }

export interface PlannerApi {
  prog: ProgrammeInfo;
  form: PlanForm;
  view: View; setView: (v: View) => void;
  step: number; setStep: (n: number) => void;
  animate: boolean;
  update: (change: (plan: Plan) => Plan) => void;
  moveModule: (code: string, index: number) => void;
  removeModule: (code: string) => void;
  markCompleted: (code: string) => void;
  reopenCompleted: (code: string) => void;
  openPicker: (index: number, replace?: { code: string; requirement: string } | null) => void;
  name: (code: string) => string;
}

export const PlannerCtx = createContext<PlannerApi | null>(null);
export const usePlanner = (): PlannerApi => {
  const p = useContext(PlannerCtx);
  if (!p) throw new Error('usePlanner outside the planner');
  return p;
};
