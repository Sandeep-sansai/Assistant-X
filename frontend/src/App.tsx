import { useEffect, useState } from 'react';
import { AppProvider, useApp } from './AppContext';
import { Chat } from './components/Chat';
import { FeedbackDialog, Footer, ProfileDialog } from './components/Dialogs';
import { Feed } from './components/Feed';
import { Header } from './components/Header';
import { Hero } from './components/Hero';
import { JourneyDialog, JourneyNudge } from './components/Journey';
import { PlanPromo } from './components/PlanPromo';
import { SemesterCard } from './components/SemesterCard';
import { Splash } from './components/Splash';
import { Todos } from './components/Todos';
import { UpNext } from './components/UpNext';
import { Planner } from './planner/Planner';

function Page() {
  const { profile, openProfile } = useApp();
  const [splashDone, setSplashDone] = useState(false);
  // First visit: ask for the profile once the opening animation is over.
  useEffect(() => { if (splashDone && !profile) openProfile(true); }, [splashDone]); // eslint-disable-line react-hooks/exhaustive-deps
  // "/" jumps to the question box, like on many sites.
  useEffect(() => {
    const key = (e: KeyboardEvent) => {
      if (e.key === '/' && document.activeElement === document.body) { e.preventDefault(); (document.getElementById('heroAskInput') as HTMLInputElement | null)?.focus(); }
    };
    document.addEventListener('keydown', key);
    return () => document.removeEventListener('keydown', key);
  }, []);
  return (
    <>
      <Splash onDone={() => setSplashDone(true)} />
      <Header />
      <main className="wrap">
        <Hero />
        <div className="layout">
          <div className="stack">
            <JourneyNudge />
            <PlanPromo />
            <UpNext />
            <div className="duo side">
              <Todos />
              <SemesterCard />
            </div>
            <Feed />
          </div>
        </div>
        <Footer />
      </main>
      <Chat />
      <ProfileDialog />
      <JourneyDialog />
      <FeedbackDialog />
      <Planner />
    </>
  );
}

export default function App() {
  return <AppProvider><Page /></AppProvider>;
}
