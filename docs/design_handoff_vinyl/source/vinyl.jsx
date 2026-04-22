// vinyl.jsx — Direction 3: "Vinyl"
// Bold rethink. Enormous title typography on the player. Home as a
// magazine spread. Queue as an LP tracklist. Library indexed by A-Z rail.

function VinylHome({ dark, accent }) {
  const P = pal(dark);
  const S = SAMPLE;
  return (
    <PhoneContent>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        {/* Masthead */}
        <div style={{
          padding: '14px 24px 18px',
          borderBottom: `1px solid ${P.hair}`,
          display: 'flex', alignItems: 'baseline', justifyContent: 'space-between',
        }}>
          <div style={{ fontFamily: T.display, fontSize: 30, letterSpacing: -0.8, color: P.text, lineHeight: 1 }}>
            Source
          </div>
          <div style={{ fontFamily: T.mono, fontSize: 10, color: P.textMute, letterSpacing: 1.2 }}>THU 21.04 · 21:47</div>
        </div>

        {/* Feature track — full bleed */}
        <div style={{ position: 'relative', padding: '28px 0 0' }}>
          <div style={{ padding: '0 24px' }}>
            <div style={{ fontFamily: T.mono, fontSize: 10, color: accent, letterSpacing: 1.4 }}>SIDE A · TRACK 04</div>
            <div style={{
              fontFamily: T.display, fontSize: 96, lineHeight: 0.85, letterSpacing: -3.5,
              color: P.text, marginTop: 10, fontStyle: 'italic',
            }}>
              {S.continuing.title}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 18 }}>
              <ArtPlaceholder seed={S.continuing.seed} size={50} rounded={25} />
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 14, color: P.text }}>{S.continuing.artist}</div>
                <div style={{ fontSize: 11.5, color: P.textDim, marginTop: 1 }}>{S.continuing.album} — 2023</div>
              </div>
              <IconButton filled accent={accent} size={52}>{Icons.play}</IconButton>
            </div>
          </div>
        </div>

        {/* Editorial divider */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '34px 24px 14px' }}>
          <div style={{ fontFamily: T.mono, fontSize: 10, color: P.textMute, letterSpacing: 1.2 }}>FEATURE</div>
          <div style={{ flex: 1, height: 1, background: P.hair }} />
          <div style={{ fontFamily: T.mono, fontSize: 10, color: P.textMute }}>02 / 07</div>
        </div>

        {/* Wide editorial card */}
        <div style={{ padding: '0 24px 24px' }}>
          <ArtPlaceholder seed="citizen" size="100%" rounded={4} style={{ aspectRatio: '3 / 2', height: 'auto' }} />
          <div style={{ fontFamily: T.display, fontSize: 32, letterSpacing: -0.6, color: P.text, marginTop: 16, lineHeight: 1.05 }}>
            <span style={{ fontStyle: 'italic' }}>Citizen of Glass</span>, revisited
          </div>
          <div style={{ fontSize: 13, color: P.textDim, marginTop: 8, lineHeight: 1.55 }}>
            Agnes Obel's third record turns nine. You've played it 42 times this year.
          </div>
          <div style={{ fontFamily: T.mono, fontSize: 10, color: accent, letterSpacing: 1.2, marginTop: 14, textTransform: 'uppercase' }}>
            Play the album →
          </div>
        </div>

        {/* Section: on rotation */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '0 24px 14px' }}>
          <div style={{ fontFamily: T.mono, fontSize: 10, color: P.textMute, letterSpacing: 1.2 }}>ON ROTATION</div>
          <div style={{ flex: 1, height: 1, background: P.hair }} />
        </div>
        <div style={{ display: 'flex', gap: 14, padding: '0 24px 30px', overflowX: 'auto' }}>
          {S.albums.slice(0, 5).map((a, i) => (
            <div key={i} style={{ width: 160, flexShrink: 0 }}>
              <ArtPlaceholder seed={a.seed} size={160} rounded={2} />
              <div style={{ fontFamily: T.display, fontStyle: 'italic', fontSize: 18, letterSpacing: -0.2, color: P.text, marginTop: 10, lineHeight: 1.15 }}>{a.title}</div>
              <div style={{ fontSize: 11, color: P.textDim, marginTop: 2 }}>{a.artist}</div>
            </div>
          ))}
        </div>

        {/* Section: columns of tracks */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '0 24px 14px' }}>
          <div style={{ fontFamily: T.mono, fontSize: 10, color: P.textMute, letterSpacing: 1.2 }}>LATE LISTENS</div>
          <div style={{ flex: 1, height: 1, background: P.hair }} />
        </div>
        <div style={{ padding: '0 24px 40px' }}>
          {S.tracks.slice(2, 7).map((t, i) => (
            <div key={i} style={{
              display: 'grid', gridTemplateColumns: 'auto 1fr auto', gap: 14, alignItems: 'baseline',
              padding: '14px 0', borderBottom: i < 4 ? `1px solid ${P.hair}` : 'none',
            }}>
              <Num style={{ fontSize: 10, color: P.textMute }}>{String(i + 1).padStart(2, '0')}</Num>
              <div>
                <div style={{ fontFamily: T.display, fontSize: 22, letterSpacing: -0.3, color: P.text, lineHeight: 1.1 }}>{t.title}</div>
                <div style={{ fontSize: 11.5, color: P.textDim, marginTop: 3 }}>{t.artist}</div>
              </div>
              <Num style={{ fontSize: 11, color: P.textMute }}>{t.dur}</Num>
            </div>
          ))}
        </div>
      </div>
      <MiniPlayer track={SAMPLE.continuing.title} artist={SAMPLE.continuing.artist} progress={0.42} dark={dark} accent={accent} variant="vinyl" seed={SAMPLE.continuing.seed} />
      <TabBar active="home" dark={dark} accent={accent} />
    </PhoneContent>
  );
}
window.VinylHome = VinylHome;

function VinylLibrary({ dark, accent }) {
  const P = pal(dark);
  const [tab, setTab] = React.useState('artists');
  const tabs = ['Songs', 'Albums', 'Artists', 'Playlists', 'Genres'];
  const letters = ['A', 'B', 'C', 'D', 'F', 'J', 'M', 'R', 'W'];
  return (
    <PhoneContent>
      <div style={{ padding: '14px 24px 8px' }}>
        <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between' }}>
          <div style={{ fontFamily: T.display, fontSize: 32, letterSpacing: -0.6, color: P.text }}>Library</div>
          <div style={{ fontFamily: T.mono, fontSize: 10, color: P.textMute, letterSpacing: 1 }}>A-Z</div>
        </div>
      </div>
      <div style={{ display: 'flex', gap: 6, padding: '4px 24px 14px', borderBottom: `1px solid ${P.hair}`, overflowX: 'auto' }}>
        {tabs.map(t => {
          const on = t.toLowerCase() === tab;
          return (
            <div key={t} onClick={() => setTab(t.toLowerCase())} style={{
              padding: '6px 12px', fontSize: 12, fontFamily: T.body, fontWeight: 500,
              color: on ? (dark ? T.bg : T.bgL) : P.textDim,
              background: on ? P.text : 'transparent',
              borderRadius: 100, cursor: 'pointer', whiteSpace: 'nowrap',
            }}>{t}</div>
          );
        })}
      </div>

      <div style={{ flex: 1, display: 'flex', minHeight: 0 }}>
        <div style={{ flex: 1, overflowY: 'auto' }}>
          {SAMPLE.artists.map((a, i) => (
            <div key={i}>
              {(i === 0 || SAMPLE.artists[i].name[0] !== SAMPLE.artists[i - 1]?.name[0]) && (
                <div style={{
                  fontFamily: T.display, fontSize: 64, letterSpacing: -1.6, color: accent,
                  padding: '14px 24px 0', lineHeight: 1, fontStyle: 'italic',
                }}>{a.name[0]}</div>
              )}
              <div style={{
                display: 'flex', alignItems: 'center', gap: 14,
                padding: '14px 24px', borderBottom: `1px solid ${P.hair}`,
              }}>
                <div style={{ flex: 1 }}>
                  <div style={{ fontFamily: T.display, fontSize: 22, letterSpacing: -0.3, color: P.text, lineHeight: 1.1 }}>{a.name}</div>
                  <div style={{ fontSize: 11, color: P.textDim, marginTop: 3 }}>{a.albums} albums · {a.songs} songs</div>
                </div>
                <div style={{ color: P.textMute, fontSize: 18 }}>→</div>
              </div>
            </div>
          ))}
        </div>
        {/* A-Z rail */}
        <div style={{
          width: 24, padding: '20px 6px', display: 'flex', flexDirection: 'column', gap: 5,
          alignItems: 'center', borderLeft: `1px solid ${P.hair}`,
        }}>
          {letters.map(l => (
            <div key={l} style={{
              fontFamily: T.mono, fontSize: 10, letterSpacing: 0.5,
              color: l === 'A' ? accent : P.textMute,
              fontWeight: l === 'A' ? 700 : 400,
            }}>{l}</div>
          ))}
        </div>
      </div>
      <MiniPlayer track={SAMPLE.continuing.title} artist={SAMPLE.continuing.artist} progress={0.42} dark={dark} accent={accent} variant="vinyl" seed={SAMPLE.continuing.seed} />
      <TabBar active="library" dark={dark} accent={accent} />
    </PhoneContent>
  );
}
window.VinylLibrary = VinylLibrary;

function VinylPlayer({ dark, accent }) {
  const P = pal(dark);
  const S = SAMPLE.continuing;
  return (
    <PhoneContent>
      {/* Ambient glow */}
      <div style={{
        position: 'absolute', inset: 0,
        background: `radial-gradient(ellipse at 30% 40%, ${accent}18, transparent 55%), radial-gradient(ellipse at 80% 80%, oklch(0.35 0.08 300)20, transparent 60%)`,
        pointerEvents: 'none',
      }} />
      <div style={{ padding: '8px 20px 0', display: 'flex', alignItems: 'center', justifyContent: 'space-between', position: 'relative', zIndex: 1 }}>
        <IconButton dark={dark}>{Icons.down}</IconButton>
        <div style={{ fontFamily: T.mono, fontSize: 9.5, letterSpacing: 1.5, color: P.textDim }}>SIDE A · 04</div>
        <IconButton dark={dark}>{Icons.queue}</IconButton>
      </div>

      {/* The massive title — the whole point */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', padding: '0 18px', position: 'relative', zIndex: 1 }}>
        <div style={{ fontFamily: T.mono, fontSize: 10, color: accent, letterSpacing: 1.5, textAlign: 'center' }}>
          NOW PLAYING
        </div>
        <div style={{
          fontFamily: T.display, fontSize: 140, lineHeight: 0.82, letterSpacing: -5,
          color: P.text, textAlign: 'center', marginTop: 20, fontStyle: 'italic',
        }}>
          {S.title}.
        </div>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10, marginTop: 22 }}>
          <ArtPlaceholder seed={S.seed} size={36} rounded={18} />
          <div style={{ fontFamily: T.display, fontSize: 19, letterSpacing: -0.3, color: P.text }}>{S.artist}</div>
        </div>
        <div style={{ fontSize: 11, color: P.textDim, textAlign: 'center', marginTop: 2 }}>
          {S.album} · 2023
        </div>
      </div>

      {/* Scrubber */}
      <div style={{ padding: '0 28px 14px', position: 'relative', zIndex: 1 }}>
        <div style={{ height: 2, background: P.hair, position: 'relative' }}>
          <div style={{ position: 'absolute', inset: 0, width: `${S.progress * 100}%`, background: accent }} />
          <div style={{
            position: 'absolute', left: `${S.progress * 100}%`, top: '50%',
            transform: 'translate(-50%, -50%)', width: 10, height: 10, borderRadius: 5, background: accent,
          }} />
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 6 }}>
          <Num style={{ fontSize: 10.5, color: P.textDim }}>{S.pos}</Num>
          <Num style={{ fontSize: 10.5, color: P.textDim }}>-2:57</Num>
        </div>
      </div>

      {/* Controls */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 28px 18px', position: 'relative', zIndex: 1 }}>
        <div style={{ color: P.textDim }}><div style={{ width: 22, height: 22 }}>{Icons.shuffle}</div></div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <IconButton dark={dark} size={52}>{Icons.prev}</IconButton>
          <IconButton filled accent={accent} size={76}>{Icons.play}</IconButton>
          <IconButton dark={dark} size={52}>{Icons.next}</IconButton>
        </div>
        <div style={{ color: P.textDim }}><div style={{ width: 22, height: 22 }}>{Icons.heart}</div></div>
      </div>
    </PhoneContent>
  );
}
window.VinylPlayer = VinylPlayer;

// Queue as LP back cover
function VinylQueue({ dark, accent }) {
  const P = pal(dark);
  return (
    <PhoneContent>
      <div style={{ padding: '16px 24px 18px' }}>
        <div style={{ fontFamily: T.mono, fontSize: 10, color: P.textMute, letterSpacing: 1.5 }}>B SIDE · TRACKLIST</div>
        <div style={{ fontFamily: T.display, fontSize: 56, letterSpacing: -1.4, color: P.text, lineHeight: 0.9, marginTop: 6, fontStyle: 'italic' }}>
          What's<br/>next.
        </div>
      </div>

      <div style={{ flex: 1, overflowY: 'auto' }}>
        <div style={{ padding: '0 24px' }}>
          {SAMPLE.tracks.map((t, i) => {
            const now = i === 1;
            return (
              <div key={i} style={{
                display: 'grid', gridTemplateColumns: '28px 1fr auto',
                gap: 14, alignItems: 'baseline',
                padding: '14px 0', borderTop: `1px solid ${P.hair}`,
                color: now ? accent : P.text,
              }}>
                <Num style={{ fontSize: 11, opacity: now ? 1 : 0.55 }}>{String(i + 1).padStart(2, '0')}</Num>
                <div style={{ minWidth: 0 }}>
                  <div style={{
                    fontFamily: T.display, fontSize: 22, letterSpacing: -0.3, lineHeight: 1.1,
                    fontStyle: now ? 'italic' : 'normal',
                    whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                  }}>
                    {t.title}{now && ' ·'}
                  </div>
                  <div style={{ fontSize: 11.5, color: now ? accent : P.textDim, marginTop: 3, opacity: now ? 0.8 : 1 }}>
                    {t.artist}
                  </div>
                </div>
                <Num style={{ fontSize: 11, opacity: now ? 0.8 : 0.6 }}>{t.dur}</Num>
              </div>
            );
          })}
          <div style={{ height: 1, background: P.hair }} />
        </div>
      </div>
    </PhoneContent>
  );
}
window.VinylQueue = VinylQueue;

function VinylSearch({ dark, accent }) {
  const P = pal(dark);
  return (
    <PhoneContent>
      <div style={{ padding: '16px 24px 8px' }}>
        <div style={{ fontFamily: T.display, fontSize: 72, letterSpacing: -2, color: P.text, lineHeight: 0.9, fontStyle: 'italic' }}>
          Find.
        </div>
      </div>
      <div style={{ padding: '18px 24px 20px' }}>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 10, padding: '10px 16px',
          border: `1px solid ${P.hair}`, borderRadius: 100, background: P.surface,
        }}>
          <div style={{ color: P.textDim, width: 16, height: 16 }}>{Icons.search}</div>
          <div style={{ flex: 1, fontSize: 14, color: P.textMute, fontFamily: T.body }}>What are you after?</div>
        </div>
      </div>

      <div style={{ padding: '0 24px 16px' }}>
        <div style={{ fontFamily: T.mono, fontSize: 10, color: P.textMute, letterSpacing: 1.2, marginBottom: 14 }}>CATEGORIES</div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
          {[
            { label: 'Ambient',    h: 40 },
            { label: 'Folk',       h: 80 },
            { label: 'Electronic', h: 260 },
            { label: 'Classical',  h: 340 },
            { label: 'Jazz',       h: 20  },
            { label: 'Post-rock',  h: 200 },
          ].map((g, i) => (
            <div key={i} style={{
              padding: '22px 16px 18px', borderRadius: 4,
              background: `oklch(0.28 0.07 ${g.h})`,
              minHeight: 110, display: 'flex', flexDirection: 'column', justifyContent: 'space-between',
            }}>
              <div style={{ fontFamily: T.mono, fontSize: 10, color: 'rgba(255,255,255,0.5)', letterSpacing: 1 }}>№ 0{i + 1}</div>
              <div style={{
                fontFamily: T.display, fontSize: 24, letterSpacing: -0.3, color: '#fff',
                fontStyle: i % 2 ? 'italic' : 'normal', lineHeight: 1.05,
              }}>{g.label}</div>
            </div>
          ))}
        </div>
      </div>
      <TabBar active="search" dark={dark} accent={accent} />
    </PhoneContent>
  );
}
window.VinylSearch = VinylSearch;

Object.assign(window, { VinylHome, VinylLibrary, VinylPlayer, VinylQueue, VinylSearch });
