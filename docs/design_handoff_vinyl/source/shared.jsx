// shared.jsx — design tokens, phone frame, icons, micro-components
// All three directions share this foundation: warm palette, Instrument
// Serif display + Geist body + Geist Mono numerics, 6/12/18/28 radii.

// ─── Tokens ──────────────────────────────────────────────────────
const T = {
  // Dark (default) — warm graphite, almost-black with amber undertone
  bg:        'oklch(0.145 0.008 60)',
  surface:   'oklch(0.185 0.010 60)',
  surface2:  'oklch(0.225 0.012 60)',
  hair:      'oklch(0.280 0.012 60)',
  text:      'oklch(0.965 0.005 80)',
  textDim:   'oklch(0.780 0.010 70)',
  textMute:  'oklch(0.580 0.012 70)',

  // Light — warm paper, ink
  bgL:       'oklch(0.970 0.008 80)',
  surfaceL:  'oklch(0.945 0.010 75)',
  surface2L: 'oklch(0.920 0.012 75)',
  hairL:     'oklch(0.870 0.012 75)',
  textL:     'oklch(0.200 0.012 60)',
  textDimL:  'oklch(0.420 0.012 65)',
  textMuteL: 'oklch(0.580 0.012 70)',

  // Accent — default warm amber; Tweak swaps this
  accent:    'oklch(0.74 0.14 60)',

  // Type
  display: '"Instrument Serif", "EB Garamond", Georgia, serif',
  body:    '"Geist", "Inter Tight", -apple-system, sans-serif',
  mono:    '"Geist Mono", "JetBrains Mono", ui-monospace, monospace',
};
window.T = T;

// Resolve tokens for current mode
function pal(dark = true) {
  return dark
    ? { bg: T.bg, surface: T.surface, surface2: T.surface2, hair: T.hair, text: T.text, textDim: T.textDim, textMute: T.textMute }
    : { bg: T.bgL, surface: T.surfaceL, surface2: T.surface2L, hair: T.hairL, text: T.textL, textDim: T.textDimL, textMute: T.textMuteL };
}
window.pal = pal;

// ─── Icons — line-weight only, no slop ───────────────────────────
const Icon = ({ d, size = 18, stroke = 'currentColor', fill = 'none', sw = 1.5 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill={fill} stroke={stroke} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round">
    <path d={d} />
  </svg>
);
const Icons = {
  play:   <svg viewBox="0 0 24 24" fill="currentColor"><path d="M7 5.5v13a.8.8 0 0 0 1.23.67l10.4-6.5a.8.8 0 0 0 0-1.34l-10.4-6.5A.8.8 0 0 0 7 5.5z"/></svg>,
  pause:  <svg viewBox="0 0 24 24" fill="currentColor"><rect x="6.5" y="5" width="4" height="14" rx="1"/><rect x="13.5" y="5" width="4" height="14" rx="1"/></svg>,
  prev:   <Icon d="M6 5v14M19 5.5L9 12l10 6.5z" sw={1.6} fill="currentColor" />,
  next:   <Icon d="M18 5v14M5 5.5L15 12 5 18.5z" sw={1.6} fill="currentColor" />,
  shuffle:<Icon d="M16 4h4v4M4 20l16-16M20 16v4h-4M15 15l5 5M4 4l5 5" />,
  repeat: <Icon d="M17 3l3 3-3 3M4 13V9a3 3 0 0 1 3-3h13M7 21l-3-3 3-3M20 11v4a3 3 0 0 1-3 3H4" />,
  heart:  <Icon d="M20.8 7.6a5.5 5.5 0 0 0-9-1.8l-.1.1-.1-.1a5.5 5.5 0 0 0-7.8 7.7l7.2 7.3a.8.8 0 0 0 1.2 0l7.2-7.3a5.5 5.5 0 0 0 .4-5.9z" />,
  heartF: <Icon d="M20.8 7.6a5.5 5.5 0 0 0-9-1.8l-.1.1-.1-.1a5.5 5.5 0 0 0-7.8 7.7l7.2 7.3a.8.8 0 0 0 1.2 0l7.2-7.3a5.5 5.5 0 0 0 .4-5.9z" fill="currentColor" />,
  search: <Icon d="M11 4a7 7 0 1 1-4.95 11.95L3 19M11 4a7 7 0 0 1 6.95 7A7 7 0 0 1 11 18" />,
  home:   <Icon d="M4 11l8-7 8 7v9a1 1 0 0 1-1 1h-4v-7h-6v7H5a1 1 0 0 1-1-1v-9z" />,
  library:<Icon d="M4 5h3v14H4zM10 5h3v14h-3zM17 7l3 12-2.8.7L14 7.7z" />,
  queue:  <Icon d="M3 6h13M3 12h13M3 18h8M17 14v7l5-3z" sw={1.6} />,
  dots:   <svg viewBox="0 0 24 24" fill="currentColor"><circle cx="5" cy="12" r="1.6"/><circle cx="12" cy="12" r="1.6"/><circle cx="19" cy="12" r="1.6"/></svg>,
  down:   <Icon d="M6 9l6 6 6-6" sw={1.8} />,
  up:     <Icon d="M6 15l6-6 6 6" sw={1.8} />,
  more:   <Icon d="M5 7h14M5 12h14M5 17h14" sw={1.6} />,
  cast:   <Icon d="M3 7V5a1 1 0 0 1 1-1h16a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-7M3 11a8 8 0 0 1 8 8M3 15a4 4 0 0 1 4 4M3 19h.01" />,
  grid:   <Icon d="M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z" />,
  list:   <Icon d="M3 6h18M3 12h18M3 18h18" sw={1.6} />,
  folder: <Icon d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z" />,
  settings: <Icon d="M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z" />,
  edit:   <Icon d="M11 4H5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-6M18.5 2.5a2.1 2.1 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />,
  mic:    <Icon d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3zM19 10v2a7 7 0 0 1-14 0v-2M12 19v4M8 23h8" />,
};
window.Icons = Icons;

// ─── Album-art placeholder — subtle stripes, no AI slop ──────────
// Deterministic hue per seed; two-tone warm stripes.
function artHue(seed) {
  let h = 0;
  for (let i = 0; i < (seed || '').length; i++) h = (h * 31 + seed.charCodeAt(i)) % 360;
  return h;
}
function ArtPlaceholder({ seed = 'a', label, size = '100%', rounded = 2, style = {} }) {
  const h = artHue(seed);
  const a = `oklch(0.45 0.08 ${h})`;
  const b = `oklch(0.32 0.06 ${h})`;
  const c = `oklch(0.60 0.09 ${h})`;
  return (
    <div style={{
      width: size, height: size, borderRadius: rounded,
      background: `linear-gradient(135deg, ${a} 0%, ${b} 100%)`,
      position: 'relative', overflow: 'hidden', flexShrink: 0,
      ...style,
    }}>
      <div style={{
        position: 'absolute', inset: 0,
        backgroundImage: `repeating-linear-gradient(45deg, transparent 0 14px, ${c}22 14px 15px)`,
      }} />
      {label && (
        <div style={{
          position: 'absolute', left: 8, bottom: 6,
          fontFamily: T.mono, fontSize: 9, letterSpacing: 0.5,
          color: 'rgba(255,255,255,0.55)', textTransform: 'uppercase',
        }}>{label}</div>
      )}
    </div>
  );
}
window.ArtPlaceholder = ArtPlaceholder;

// ─── Phone frame — minimal, warm, respects the aesthetic ─────────
// 390×844 iPhone-ish dimensions, tiny bezel, warm frame color.
// Not Material-default; we own this aesthetic.
function PhoneFrame({ children, dark = true, label, w = 390, h = 844 }) {
  const P = pal(dark);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 14 }}>
      <div style={{
        width: w, height: h, borderRadius: 42,
        padding: 8, background: dark ? 'oklch(0.08 0.006 60)' : 'oklch(0.82 0.006 75)',
        boxShadow: dark
          ? 'inset 0 0 0 1px oklch(0.22 0.008 60), 0 30px 60px -20px rgba(0,0,0,0.5)'
          : 'inset 0 0 0 1px oklch(0.75 0.008 75), 0 30px 60px -20px rgba(60,40,20,0.25)',
      }}>
        <div style={{
          width: '100%', height: '100%', borderRadius: 34,
          background: P.bg, color: P.text,
          fontFamily: T.body, overflow: 'hidden', position: 'relative',
          display: 'flex', flexDirection: 'column',
        }}>
          <StatusBar dark={dark} />
          {children}
          <HomeIndicator dark={dark} />
        </div>
      </div>
      {label && (
        <div style={{ fontFamily: T.mono, fontSize: 11, letterSpacing: 0.6, color: 'rgba(60,50,40,0.6)', textTransform: 'uppercase' }}>
          {label}
        </div>
      )}
    </div>
  );
}
window.PhoneFrame = PhoneFrame;

// iOS-style status bar with dynamic island
function StatusBar({ dark }) {
  const c = dark ? T.text : T.textL;
  return (
    <div style={{
      height: 50, flexShrink: 0,
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      padding: '14px 28px 0', position: 'relative',
    }}>
      <div style={{ fontFamily: T.body, fontSize: 15, fontWeight: 600, color: c, fontVariantNumeric: 'tabular-nums' }}>9:41</div>
      <div style={{
        position: 'absolute', left: '50%', top: 10, transform: 'translateX(-50%)',
        width: 110, height: 34, borderRadius: 20, background: '#000',
      }} />
      <div style={{ display: 'flex', gap: 5, alignItems: 'center', color: c }}>
        {/* signal */}
        <svg width="17" height="11" viewBox="0 0 17 11" fill="currentColor"><rect x="0" y="7" width="3" height="4" rx="0.5"/><rect x="4.5" y="5" width="3" height="6" rx="0.5"/><rect x="9" y="3" width="3" height="8" rx="0.5"/><rect x="13.5" y="0" width="3" height="11" rx="0.5"/></svg>
        {/* wifi */}
        <svg width="15" height="11" viewBox="0 0 15 11" fill="currentColor"><path d="M7.5 0a11 11 0 0 0-7.5 3L7.5 11 15 3A11 11 0 0 0 7.5 0zm0 5a4 4 0 0 0-2.8 1.2L7.5 9l2.8-2.8A4 4 0 0 0 7.5 5z" opacity="0.3"/><path d="M7.5 5a4 4 0 0 0-2.8 1.2L7.5 9l2.8-2.8A4 4 0 0 0 7.5 5z"/></svg>
        {/* battery */}
        <svg width="27" height="12" viewBox="0 0 27 12"><rect x="0.5" y="0.5" width="22" height="11" rx="3" fill="none" stroke="currentColor" opacity="0.4"/><rect x="2" y="2" width="19" height="8" rx="1.5" fill="currentColor"/><rect x="23.5" y="4" width="1.5" height="4" rx="0.5" fill="currentColor" opacity="0.5"/></svg>
      </div>
    </div>
  );
}

function HomeIndicator({ dark }) {
  return (
    <div style={{ height: 28, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div style={{ width: 130, height: 4.5, borderRadius: 3, background: dark ? 'rgba(255,245,230,0.85)' : 'rgba(30,20,10,0.85)' }} />
    </div>
  );
}

// ─── Shared micro-components ─────────────────────────────────────

// Bottom tab bar — shared across all directions
function TabBar({ active = 'home', dark = true, accent }) {
  const P = pal(dark);
  const tabs = [
    { id: 'home', label: 'Home', icon: Icons.home },
    { id: 'search', label: 'Search', icon: Icons.search },
    { id: 'library', label: 'Library', icon: Icons.library },
    { id: 'settings', label: 'Settings', icon: Icons.settings },
  ];
  return (
    <div style={{
      display: 'flex', padding: '10px 8px 6px', gap: 4,
      borderTop: `1px solid ${P.hair}`,
      background: dark ? `${T.bg}ee` : `${T.bgL}ee`,
      backdropFilter: 'blur(18px)',
    }}>
      {tabs.map(t => {
        const on = t.id === active;
        return (
          <div key={t.id} style={{
            flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 3,
            padding: '6px 0', color: on ? accent : P.textMute,
          }}>
            <div style={{ width: 22, height: 22 }}>{t.icon}</div>
            <div style={{ fontSize: 10.5, fontFamily: T.body, fontWeight: on ? 600 : 500, letterSpacing: 0.2 }}>{t.label}</div>
          </div>
        );
      })}
    </div>
  );
}
window.TabBar = TabBar;

// Mini player bar — varies subtly per direction via `variant` prop
function MiniPlayer({ track = 'Ribbon', artist = 'Midas Fall', progress = 0.42, dark = true, accent, variant = 'paper', seed = 'ribbon' }) {
  const P = pal(dark);
  const isVinyl = variant === 'vinyl';
  const isStudio = variant === 'studio';
  return (
    <div style={{
      margin: isVinyl ? 0 : '0 10px 8px', padding: 0,
      borderRadius: isVinyl ? 0 : 18,
      background: isVinyl ? 'transparent' : P.surface,
      border: isVinyl ? 'none' : `1px solid ${P.hair}`,
      borderTop: isVinyl ? `1px solid ${P.hair}` : undefined,
      overflow: 'hidden', position: 'relative',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '10px 12px' }}>
        <ArtPlaceholder seed={seed} size={44} rounded={isStudio ? 2 : 8} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{
            fontFamily: isStudio ? T.display : T.body,
            fontSize: isStudio ? 16 : 14, fontWeight: isStudio ? 400 : 600,
            fontStyle: isStudio ? 'italic' : 'normal',
            color: P.text, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', letterSpacing: isStudio ? -0.2 : 0,
          }}>{track}</div>
          <div style={{ fontSize: 11.5, color: P.textDim, fontFamily: T.body, marginTop: 1, letterSpacing: 0.1 }}>{artist}</div>
        </div>
        <div style={{ display: 'flex', gap: 2, color: P.text }}>
          <IconButton dark={dark} size={36}>{Icons.play}</IconButton>
          <IconButton dark={dark} size={36}>{Icons.next}</IconButton>
        </div>
      </div>
      <div style={{ height: 2, background: P.hair, position: 'relative' }}>
        <div style={{ position: 'absolute', inset: 0, width: `${progress * 100}%`, background: accent }} />
      </div>
    </div>
  );
}
window.MiniPlayer = MiniPlayer;

function IconButton({ children, size = 40, dark = true, filled = false, accent, onClick }) {
  const P = pal(dark);
  return (
    <button onClick={onClick} style={{
      width: size, height: size, borderRadius: size,
      background: filled ? accent : 'transparent',
      color: filled ? (dark ? T.bg : T.bgL) : P.text,
      border: 'none', display: 'flex', alignItems: 'center', justifyContent: 'center',
      cursor: 'pointer', flexShrink: 0, padding: 0,
    }}>
      <div style={{ width: size * 0.52, height: size * 0.52, display: 'flex' }}>{children}</div>
    </button>
  );
}
window.IconButton = IconButton;

// Helper for right-aligned subtle tabular numerics (timestamps)
function Num({ children, style = {} }) {
  return <span style={{ fontFamily: T.mono, fontVariantNumeric: 'tabular-nums', letterSpacing: 0.2, ...style }}>{children}</span>;
}
window.Num = Num;

// ─── Sample data ─────────────────────────────────────────────────
const SAMPLE = {
  continuing: { title: 'Ribbon', artist: 'Midas Fall', album: 'Eternal Wings', seed: 'ribbon', progress: 0.42, pos: '2:17', dur: '5:14' },
  tracks: [
    { title: 'The Cartographer', artist: 'Agnes Obel', album: 'Citizen of Glass', dur: '4:22', seed: 'carto' },
    { title: 'Ribbon', artist: 'Midas Fall', album: 'Eternal Wings', dur: '5:14', seed: 'ribbon' },
    { title: 'Glass Ocean', artist: 'Lowswimmer', album: 'Afterglow', dur: '3:48', seed: 'glass' },
    { title: 'Hollow Road', artist: 'Daughter', album: 'Not To Disappear', dur: '5:02', seed: 'hollow' },
    { title: 'Architect', artist: 'Rosie Lowe', album: 'YU', dur: '3:31', seed: 'archit' },
    { title: 'Undertow', artist: 'Warpaint', album: 'Heads Up', dur: '4:56', seed: 'underto' },
    { title: 'Pale Blue', artist: 'The Japanese House', album: 'In The End', dur: '4:11', seed: 'pale' },
    { title: 'Slow Dancer', artist: 'Boy & Bear', album: 'Suck On Light', dur: '3:44', seed: 'slowdance' },
    { title: 'Evening Song', artist: 'Julianna Barwick', album: 'Healing Is A Miracle', dur: '5:28', seed: 'evening' },
  ],
  albums: [
    { title: 'Citizen of Glass', artist: 'Agnes Obel', year: '2016', seed: 'citizen' },
    { title: 'Eternal Wings',    artist: 'Midas Fall',   year: '2023', seed: 'eternal' },
    { title: 'Afterglow',        artist: 'Lowswimmer',   year: '2021', seed: 'after' },
    { title: 'Not To Disappear', artist: 'Daughter',     year: '2016', seed: 'notto' },
    { title: 'YU',               artist: 'Rosie Lowe',   year: '2019', seed: 'yurl' },
    { title: 'Heads Up',         artist: 'Warpaint',     year: '2016', seed: 'heads' },
  ],
  playlists: [
    { name: 'Late Mornings',  count: 42, seed: 'late' },
    { name: 'Nocturne',       count: 28, seed: 'noct' },
    { name: 'Rain & Writing', count: 63, seed: 'rain' },
    { name: 'Drive, North',   count: 17, seed: 'drive' },
  ],
  artists: [
    { name: 'Agnes Obel',     albums: 4,  songs: 38 },
    { name: 'Bon Iver',       albums: 6,  songs: 52 },
    { name: 'Daughter',       albums: 3,  songs: 27 },
    { name: 'Fleet Foxes',    albums: 5,  songs: 44 },
    { name: 'Julianna Barwick', albums: 4, songs: 31 },
    { name: 'Midas Fall',     albums: 5,  songs: 49 },
  ],
};
window.SAMPLE = SAMPLE;

// Content wrapper for phone scrollable area
function PhoneContent({ children, style = {} }) {
  return (
    <div style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column', ...style }}>
      {children}
    </div>
  );
}
window.PhoneContent = PhoneContent;

Object.assign(window, { T, pal, Icon, Icons, ArtPlaceholder, PhoneFrame, TabBar, MiniPlayer, IconButton, Num, SAMPLE, PhoneContent });
