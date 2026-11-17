import fs from 'node:fs';

const data = JSON.parse(fs.readFileSync('docs/referencia/catalogo-original.json', 'utf8'));
const escapeSql = (value) => value == null || value === '' ? 'NULL' : `'${String(value).replaceAll("'", "''")}'`;
const slugify = (value) => String(value).trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
const categories = data.categories.filter((item) => item.id !== 'all');
const orbit = data.orbit.map((item, index) => ({
  name: item.n,
  slug: slugify(item.n),
  logo: item.url,
  color: item.c,
  featured: 1,
  order: index,
}));
const known = new Map(orbit.map((item) => [item.slug, item]));
for (const product of data.products) {
  const key = slugify(product.brand);
  if (!known.has(key)) {
    known.set(key, {
      name: product.brand[0].toUpperCase() + product.brand.slice(1).toLowerCase(),
      slug: key,
      logo: null,
      color: null,
      featured: 0,
      order: known.size,
    });
  }
}

const lines = [
  '-- Generated from docs/referencia/catalogo-original.json; do not edit manually.',
  'INSERT INTO brands (name, slug, logo_url, display_color, featured, display_order) VALUES',
  [...known.values()].map((brand) => `  (${escapeSql(brand.name)}, ${escapeSql(brand.slug)}, ${escapeSql(brand.logo)}, ${escapeSql(brand.color)}, ${brand.featured}, ${brand.order})`).join(',\n') + ';',
  '',
  'INSERT INTO categories (name, slug, icon_key, display_order) VALUES',
  categories.map((category, index) => `  (${escapeSql(category.label)}, ${escapeSql(category.id)}, ${escapeSql(category.id)}, ${index})`).join(',\n') + ';',
  '',
  'INSERT INTO products (slug, sku, name, description, brand_id, category_id, badge, image_url, display_order)',
  'VALUES',
  data.products.map((product, index) => `  (${escapeSql(product.id)}, ${escapeSql(product.sku)}, ${escapeSql(product.name)}, ${escapeSql(product.desc)}, (SELECT id FROM brands WHERE slug = ${escapeSql(slugify(product.brand))}), (SELECT id FROM categories WHERE slug = ${escapeSql(product.cat)}), ${escapeSql(product.badge ?? null)}, ${escapeSql(product.img ?? null)}, ${index})`).join(',\n') + ';',
];
process.stdout.write(lines.join('\n') + '\n');
