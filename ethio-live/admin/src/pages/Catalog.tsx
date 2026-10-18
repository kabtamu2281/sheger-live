import { useState } from 'react';
import { rpc } from '../supabase';
import { formatEtb } from '../errors';
import type { StaffRole } from '../roles';
import { ErrorBox, Loadable, Modal, Page, Select, Table, useAction, useLoad } from '../ui';

interface Gift {
  id: string; name: string; name_am: string; category: string; price_coins: number; artwork_key: string; animation_key: string;
  is_animated_fullscreen: boolean; available: boolean; sort_order: number; event_id: string | null;
}
interface CoinPackage {
  id: string; coins: number; bonus_promo_coins: number; price_minor: number; currency: string; play_product_id: string | null;
  active: boolean; sort_order: number;
}
const GIFT_CATEGORIES = ['free', 'basic', 'premium', 'event', 'seasonal', 'creator_favorite'] as const;
// Artwork is drawn in the Android app (ui/gifts/GiftArt.kt); unknown keys fall back to a gift box.
const ARTWORK = ['adey', 'clap', 'buna', 'rose', 'heart', 'injera', 'jebena', 'bloom', 'kebero', 'demera', 'gelada', 'lion', 'crown', 'lalibela'] as const;
const ANIMATIONS = ['float', 'pop', 'burst', 'pour', 'shake', 'flame', 'jump', 'roar', 'shine', 'rise'] as const;

export function Catalog({ role }: { role: StaffRole }) {
  const state = useLoad(() => rpc<{ gifts: Gift[]; coin_packages: CoinPackage[] }>('staff_catalog'));
  const [gift, setGift] = useState<Gift | null>(null);
  const [pkg, setPkg] = useState<CoinPackage | null>(null);
  const canGifts = role === 'admin' || role === 'superadmin';
  const canPackages = role === 'finance' || role === 'superadmin';
  return (
    <Page title="Gifts & coins">
      <Loadable state={state}>{(c) => <>
        <div className="page-head"><h2>Gifts</h2>{canGifts && <button onClick={() => setGift({ id: '', name: '', name_am: '', category: 'basic', price_coins: 10, artwork_key: 'buna',
          animation_key: 'float', is_animated_fullscreen: false, available: true, sort_order: 100, event_id: null })}>New gift</button>}</div>
        <Table rowKey={(g) => g.id} rows={c.gifts} columns={[
          { key: 'id', label: 'ID', render: (g) => canGifts ? <button className="link" onClick={() => setGift(g)}>{g.id}</button> : g.id },
          { key: 'name', label: 'Name', render: (g) => `${g.name} · ${g.name_am}` },
          { key: 'category', label: 'Category' },
          { key: 'price_coins', label: 'Coins' },
          { key: 'animation_key', label: 'Animation', render: (g) => g.animation_key + (g.is_animated_fullscreen ? ' (full screen)' : '') },
          { key: 'available', label: 'Available', render: (g) => g.available ? <span className="pill green">yes</span> : <span className="pill red">no</span> },
        ]} />
        <div className="page-head" style={{ marginTop: 20 }}><h2>Coin packages</h2>{canPackages && <button onClick={() => setPkg({ id: '', coins: 100, bonus_promo_coins: 0,
          price_minor: 4900, currency: 'ETB', play_product_id: null, active: true, sort_order: 100 })}>New package</button>}</div>
        <p className="muted">Google Play prices are set in Play Console per product ID; the price here is what the app shows for Chapa and what Play purchases are checked against. Changes need finance and two-step verification.</p>
        <Table rowKey={(p) => p.id} rows={c.coin_packages} columns={[
          { key: 'id', label: 'ID', render: (p) => canPackages ? <button className="link" onClick={() => setPkg(p)}>{p.id}</button> : p.id },
          { key: 'coins', label: 'Coins', render: (p) => p.coins + (p.bonus_promo_coins ? ` + ${p.bonus_promo_coins} bonus` : '') },
          { key: 'price_minor', label: 'Price', render: (p) => p.currency === 'ETB' ? formatEtb(p.price_minor) : `${(p.price_minor / 100).toFixed(2)} ${p.currency}` },
          { key: 'play_product_id', label: 'Play product' },
          { key: 'active', label: 'Active', render: (p) => p.active ? 'yes' : 'no' },
        ]} />
      </>}</Loadable>
      {gift && <GiftEditor gift={gift} onClose={() => { setGift(null); void state.reload(); }} />}
      {pkg && <PackageEditor pkg={pkg} onClose={() => { setPkg(null); void state.reload(); }} />}
    </Page>
  );
}

function GiftEditor({ gift, onClose }: { gift: Gift; onClose: () => void }) {
  const [g, setG] = useState(gift);
  const action = useAction();
  const isNew = gift.id === '';
  const set = <K extends keyof Gift>(k: K, v: Gift[K]) => setG({ ...g, [k]: v });
  return (
    <Modal title={isNew ? 'New gift' : `Edit ${gift.id}`} onClose={onClose}>
      <div className="stack">
        <div className="grid2">
          <label className="field"><span>ID (a-z, 0-9, _)</span><input value={g.id} disabled={!isNew} onChange={(e) => set('id', e.target.value.toLowerCase())} /></label>
          <label className="field"><span>Name (English)</span><input value={g.name} onChange={(e) => set('name', e.target.value)} /></label>
          <label className="field"><span>Name (Amharic)</span><input value={g.name_am} onChange={(e) => set('name_am', e.target.value)} /></label>
          <label className="field"><span>Price in coins (0 only for free)</span><input type="number" min={0} value={g.price_coins} onChange={(e) => set('price_coins', Number(e.target.value))} /></label>
          <label className="field"><span>Sort order</span><input type="number" value={g.sort_order} onChange={(e) => set('sort_order', Number(e.target.value))} /></label>
        </div>
        <div className="row">
          <Select label="Category" value={g.category as (typeof GIFT_CATEGORIES)[number]} options={GIFT_CATEGORIES} onChange={(v) => set('category', v)} />
          <Select label="Artwork" value={g.artwork_key as (typeof ARTWORK)[number]} options={ARTWORK} onChange={(v) => set('artwork_key', v)} />
          <Select label="Animation" value={g.animation_key as (typeof ANIMATIONS)[number]} options={ANIMATIONS} onChange={(v) => set('animation_key', v)} />
        </div>
        <label className="inline"><input type="checkbox" checked={g.is_animated_fullscreen} onChange={(e) => set('is_animated_fullscreen', e.target.checked)} /> Full-screen animation</label>
        <label className="inline"><input type="checkbox" checked={g.available} onChange={(e) => set('available', e.target.checked)} /> Available in the app</label>
        <ErrorBox message={action.error} />
        <button disabled={action.busy || !g.id || !g.name || !g.name_am} onClick={() => action.run(() => rpc('staff_upsert_gift', { p: g }), onClose)}>Save gift</button>
      </div>
    </Modal>
  );
}

function PackageEditor({ pkg, onClose }: { pkg: CoinPackage; onClose: () => void }) {
  const [p, setP] = useState(pkg);
  const action = useAction();
  const isNew = pkg.id === '';
  const set = <K extends keyof CoinPackage>(k: K, v: CoinPackage[K]) => setP({ ...p, [k]: v });
  return (
    <Modal title={isNew ? 'New coin package' : `Edit ${pkg.id}`} onClose={onClose}>
      <div className="stack">
        <div className="grid2">
          <label className="field"><span>ID</span><input value={p.id} disabled={!isNew} onChange={(e) => set('id', e.target.value)} /></label>
          <label className="field"><span>Coins</span><input type="number" min={1} value={p.coins} onChange={(e) => set('coins', Number(e.target.value))} /></label>
          <label className="field"><span>Bonus coins (gift-only)</span><input type="number" min={0} value={p.bonus_promo_coins} onChange={(e) => set('bonus_promo_coins', Number(e.target.value))} /></label>
          <label className="field"><span>Price in minor units (ETB cents)</span><input type="number" min={1} value={p.price_minor} onChange={(e) => set('price_minor', Number(e.target.value))} /></label>
          <label className="field"><span>Currency</span><input value={p.currency} onChange={(e) => set('currency', e.target.value.toUpperCase())} /></label>
          <label className="field"><span>Google Play product ID</span><input value={p.play_product_id ?? ''} onChange={(e) => set('play_product_id', e.target.value || null)} /></label>
          <label className="field"><span>Sort order</span><input type="number" value={p.sort_order} onChange={(e) => set('sort_order', Number(e.target.value))} /></label>
        </div>
        <p className="muted">Shown as {p.currency === 'ETB' ? formatEtb(p.price_minor) : `${(p.price_minor / 100).toFixed(2)} ${p.currency}`} for {p.coins} coins.</p>
        <label className="inline"><input type="checkbox" checked={p.active} onChange={(e) => set('active', e.target.checked)} /> Active</label>
        <ErrorBox message={action.error} />
        <button disabled={action.busy || !p.id} onClick={() => action.run(() => rpc('staff_upsert_coin_package', { p }), onClose)}>Save package</button>
      </div>
    </Modal>
  );
}
