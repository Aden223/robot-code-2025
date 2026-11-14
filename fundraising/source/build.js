// Builds the Complete Robotics fundraising PDFs from HTML.
// Usage: node build.js            (from this folder)
// Photos live in ../photos (robot_on_field, printed_shooter, printed_gears, cardboard_prototype).
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const OUT = path.resolve(__dirname, '..');
const PHOTOS = path.resolve(__dirname, '..', 'photos');

const CONTACT = {
  name: 'Sarah Rice',
  email: 'SarahRice@CompleteWorship.org',
  web: 'CompleteWorship.org/programs/stem',
  fb: 'Complete Robotics',
};
const PAYEE = 'Complete Robotics Foundation';

const css = `
@page { size: Letter; margin: 0; }
* { box-sizing: border-box; margin: 0; padding: 0; }
:root {
  --ink: #16201a; --muted: #56635b; --line: #dfe7e1;
  --g900: #12422a; --g700: #1d6b3f; --g500: #2f9a5c; --g200: #cfe9d8; --g100: #e9f5ee; --g50: #f5faf7;
}
html { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
body { font-family: Inter, sans-serif; color: var(--ink); font-size: 10.5pt; line-height: 1.5; }
.page { width: 8.5in; height: 11in; padding: 0.6in 0.7in; position: relative; overflow: hidden; page-break-after: always; }
.page:last-child { page-break-after: auto; }
h1 { font-family: 'Inter Display', Inter, sans-serif; font-weight: 700; font-size: 30pt; line-height: 1.05; letter-spacing: -0.02em; }
h2 { font-family: 'Inter Display', Inter, sans-serif; font-weight: 650; font-size: 15pt; letter-spacing: -0.01em; margin-bottom: 8px; }
h3 { font-weight: 650; font-size: 11pt; margin-bottom: 4px; }
p { margin-bottom: 9px; }
.muted { color: var(--muted); }
.small { font-size: 9pt; }

/* brand bar */
.brand { display: flex; justify-content: space-between; align-items: center; padding-bottom: 14px; border-bottom: 1px solid var(--line); margin-bottom: 20px; }
.mark { display: flex; align-items: center; gap: 10px; }
.chip { background: var(--g700); color: #fff; font-weight: 700; font-size: 9pt; padding: 5px 9px; border-radius: 8px; letter-spacing: 0.02em; }
.mark b { font-size: 12pt; font-weight: 700; }
.mark span { display: block; font-size: 8.5pt; color: var(--muted); }
.brand .right { text-align: right; font-size: 8.5pt; color: var(--muted); line-height: 1.45; }

/* packet */
.lead { font-size: 12pt; color: #2b3830; max-width: 6.3in; margin: 12px 0 22px; }
.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 24px; }
.stat { background: var(--g50); border: 1px solid var(--g200); border-radius: 14px; padding: 12px 12px 11px; }
.stat b { display: block; font-family: 'Inter Display', Inter; font-size: 19pt; font-weight: 700; color: var(--g700); line-height: 1.1; margin-bottom: 4px; }
.stat span { font-size: 8.5pt; color: var(--muted); line-height: 1.35; display: block; }
.two { display: grid; grid-template-columns: 1fr 1fr; gap: 26px; }
.hero { display: grid; grid-template-columns: 1fr 2.75in; gap: 20px; margin: 10px 0 16px; align-items: start; }
.hero .lead { margin: 0 0 14px; font-size: 11pt; }
.hero .stats { grid-template-columns: 1fr 1fr; margin: 0; gap: 8px; }
.hero .stat { padding: 9px 11px; }
.hero .stat b { font-size: 16pt; }
figure img { width: 100%; display: block; border-radius: 14px; object-fit: cover; }
figcaption { font-size: 8pt; color: var(--muted); margin-top: 5px; }
.hero figure img { height: 2.75in; object-position: center; }
.strip { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 4px 0 16px; }
.strip img { height: 1.3in; }
.budget { border: 1px solid var(--line); border-radius: 14px; overflow: hidden; }
.budget div { display: flex; justify-content: space-between; padding: 4px 12px; border-bottom: 1px solid var(--line); font-size: 9.5pt; }
.budget div:last-child { border-bottom: 0; background: var(--g900); color: #fff; font-weight: 650; }
.budget div span:last-child { font-variant-numeric: tabular-nums; }

.tiers { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 6px 0 18px; }
.tier { border-radius: 18px; padding: 16px 16px 18px; border: 1px solid var(--g200); background: #fff; }
.tier.t1 { background: var(--g900); color: #fff; border-color: var(--g900); }
.tier.t2 { background: var(--g100); }
.tier .name { font-weight: 650; font-size: 11pt; }
.pill { display: inline-block; margin: 10px 0 14px; padding: 6px 14px; border-radius: 999px; font-weight: 700; font-size: 13pt; background: #fff; color: var(--g900); border: 1px solid var(--g200); }
.tier.t1 .pill { border-color: #fff; }
.tier ul { list-style: none; }
.tier li { position: relative; padding-left: 18px; margin-bottom: 6px; font-size: 9.5pt; line-height: 1.35; }
.tier li::before { content: ''; position: absolute; left: 0; top: 5px; width: 8px; height: 8px; border-radius: 50%; background: var(--g500); }
.tier.t1 li::before { background: #8fd6aa; }
.help { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 18px; }
.help > div { background: var(--g50); border-radius: 14px; padding: 13px 14px; }
.help p { font-size: 9pt; color: var(--muted); margin: 0; }
.give { border-radius: 18px; background: var(--g100); padding: 18px 20px; display: grid; grid-template-columns: 1.1fr 1fr; gap: 20px; }
.give p { margin-bottom: 4px; font-size: 9.5pt; }
.give .k { color: var(--muted); font-size: 8.5pt; margin-bottom: 1px; }

/* letters */
.letter { font-size: 10.5pt; line-height: 1.55; }
.letter .date { margin-bottom: 18px; }
.letter .to { margin-bottom: 18px; line-height: 1.4; }
.letter p { margin-bottom: 11px; }
.sig { margin-top: 18px; }
.sigs { display: flex; gap: 40px; margin: 34px 0 6px; }
.sigs div { width: 2.2in; border-top: 1px solid #9aa79f; padding-top: 4px; font-size: 8.5pt; color: var(--muted); }
.foot { position: absolute; left: 0.7in; right: 0.7in; bottom: 0.45in; border-top: 1px solid var(--line); padding-top: 8px; font-size: 8pt; color: var(--muted); display: flex; justify-content: space-between; }
.enc { font-size: 8.5pt; color: var(--muted); margin-top: 14px; }
table.b { width: 100%; border-collapse: separate; border-spacing: 0; border: 1px solid var(--line); border-radius: 12px; overflow: hidden; margin: 6px 0 14px; font-size: 9.5pt; }
table.b td { padding: 7px 12px; border-bottom: 1px solid var(--line); }
table.b td:last-child { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
table.b tr:last-child td { border-bottom: 0; background: var(--g900); color: #fff; font-weight: 650; }
ul.dots { list-style: none; margin: 4px 0 12px; }
ul.dots li { position: relative; padding-left: 16px; margin-bottom: 5px; }
ul.dots li::before { content: ''; position: absolute; left: 0; top: 7px; width: 7px; height: 7px; border-radius: 50%; background: var(--g500); }
`;

const brand = (right) => `
<div class="brand">
  <div class="mark"><div class="chip">10978</div><div><b>Complete Robotics</b><span>FIRST Robotics Competition · Frankenmuth, Michigan</span></div></div>
  <div class="right">${right}</div>
</div>`;
const letterBrand = brand(`${CONTACT.email}<br>${CONTACT.web}`);
const foot = `<div class="foot"><span>${PAYEE} is a 501(c)(3) nonprofit. Gifts are tax-deductible.</span><span>Facebook: ${CONTACT.fb}</span></div>`;

const BUDGET = [
  ['CNC router, enclosure and tooling', 6500],
  ['Motors, controllers and electronics', 8000],
  ['Aluminum, polycarbonate and filament', 3000],
  ['FIRST registration and district events', 6000],
  ['State Championship', 1500],
  ['Worlds fund', 5000],
  ['Safety gear, tools and outreach', 1000],
];
const total = BUDGET.reduce((a, [, v]) => a + v, 0);
const $ = (n) => '$' + n.toLocaleString('en-US');

// ---------------- packet ----------------
function packet() {
  const img = (f) => `file://${PHOTOS}/${f}`;
  return `
<section class="page">
  ${brand('2026–27 Sponsorship')}
  <h1>Sponsor Complete Robotics</h1>
  <div class="hero">
    <div>
      <p class="lead">We're a high school FIRST Robotics Competition team from Frankenmuth, now in our second season. As rookies, our robot made it to the Michigan State Championship. This year we're building to qualify for the FIRST Championship in Houston, and local sponsors are how we get there.</p>
      <div class="stats">
        <div class="stat"><b>States</b><span>Qualified for the State Championship in our rookie year</span></div>
        <div class="stat"><b>6th</b><span>Seed at our Fenton district event after going 9–3</span></div>
        <div class="stat"><b>Captain</b><span>Led our own playoff alliance at Fenton</span></div>
        <div class="stat"><b>22–21</b><span>Official match record across the 2026 season</span></div>
      </div>
    </div>
    <figure><img src="${img('robot_on_field.jpg')}"><figcaption>Team 10978 on the field, 2026 season</figcaption></figure>
  </div>
  <h2>Built on a 3D printer</h2>
  <p class="muted" style="max-width:6.8in">Students design every part in CAD and write all the code in Java. With no machine shop, every custom part was printed in plastic. A CNC router lets them cut parts like these from aluminum.</p>
  <div class="strip">
    <figure><img src="${img('printed_shooter.jpg')}"><figcaption>Printed shooter assembly</figcaption></figure>
    <figure><img src="${img('printed_gears.jpg')}"><figcaption>Printed gears on a Kraken X44 motor</figcaption></figure>
    <figure><img src="${img('cardboard_prototype.jpg')}"><figcaption>Early prototype with a cardboard hopper</figcaption></figure>
  </div>
  <div class="two">
    <div>
      <h2>Where the money goes</h2>
      <p>The CNC router is the biggest single item. Parts are the biggest category: motors and electronics wear out over a season, and a Worlds-level robot needs spares.</p>
      <p class="small muted">The Worlds fund covers registration and a travel deposit. If we qualify, we'll run a separate drive to get the whole team to Houston.</p>
    </div>
    <div class="budget">
      ${BUDGET.map(([k, v]) => `<div><span>${k}</span><span>${$(v)}</span></div>`).join('')}
      <div><span>Season goal</span><span>${$(total)}</span></div>
    </div>
  </div>
</section>
<section class="page">
  ${brand('2026–27 Sponsorship')}
  <h2>Sponsorship tiers</h2>
  <p class="muted" style="max-width:6in">Every sponsor is recognized all season, from kickoff in January through our last event in the spring.</p>
  <div class="tiers">
    <div class="tier t1"><div class="name">Tier One</div><div class="pill">$1,500+</div><ul>
      <li>Logo on the robot</li><li>Logo on team t-shirts</li><li>Logo on our website</li><li>Logo displayed in our pit at competitions</li><li>Two social media feature posts</li></ul></div>
    <div class="tier t2"><div class="name">Tier Two</div><div class="pill">$500–$1,499</div><ul>
      <li>Logo on team t-shirts</li><li>Logo on our website</li><li>Logo displayed in our pit at competitions</li><li>One social media feature post</li></ul></div>
    <div class="tier"><div class="name">Tier Three</div><div class="pill">$100–$499</div><ul>
      <li>Name on team t-shirts</li><li>Name on our website</li><li>Name listed in a social media post</li></ul></div>
  </div>
  <h2>Other ways to help</h2>
  <div class="help">
    <div><h3>Parts and materials</h3><p>Aluminum, filament, tools and machine time count toward a tier at full value.</p></div>
    <div><h3>Mentors</h3><p>Engineers, machinists, programmers and business people. Many employers match volunteer hours with a cash grant.</p></div>
    <div><h3>Computers</h3><p>Laptops or desktops that can run CAD, new or refurbished, for students designing the robot.</p></div>
  </div>
  <h2>Giving</h2>
  <div class="give">
    <div>
      <p class="k">Checks payable to</p><p><b>${PAYEE}</b></p>
      <p style="margin-top:8px">We're a 501(c)(3) nonprofit, so your gift is tax-deductible. Every sponsor gets a receipt and a season-end report showing what their money paid for.</p>
    </div>
    <div>
      <p class="k">Contact</p><p><b>${CONTACT.name}</b></p><p>${CONTACT.email}</p>
      <p class="k" style="margin-top:8px">Online</p><p>${CONTACT.web}</p><p>Facebook: ${CONTACT.fb}</p>
    </div>
  </div>
</section>`;
}

// ---------------- sponsor letters ----------------
const STORY = `Last season was our first. Students designed the whole robot themselves, and since we had no machine shop, every custom part was 3D-printed. That robot still made it to the Michigan State Championship. This year we want to qualify for the FIRST Championship in Houston, which means building a stronger robot. Our season budget is ${$(total)}. The biggest single item is a CNC router, so students can machine aluminum parts instead of printing them.`;
const INTRO = `We're Complete Robotics, a high school robotics team based here in Frankenmuth. We compete in the FIRST Robotics Competition as Team 10978.`;
const CLOSE = `We'd like to stop by with the robot and show you what we're building. ${CONTACT.name} coordinates our sponsors and can be reached at ${CONTACT.email}. Our tier sheet is enclosed.`;

const L = (to, dear, custom, ask, opts = {}) => ({ to, dear, custom, ask, ...opts });
const sponsors = [
  L(['Kremin Inc.', '235 Keystone Way', 'Frankenmuth, MI 48734'], 'Kremin team',
    `CNC machining is exactly what our students want to learn next, and you do it a few miles from where we build. Kremin also sponsored Frankenmuth High School's robotics team in 2018, so you've seen what these programs do for students.`,
    `We're asking Kremin to sponsor us at Tier One ($1,500). If cash isn't possible this year, a few hours of machine time, a shop tour, or a machinist willing to teach our students CNC setup would help just as much.`),
  L(['Frankenmuth Bavarian Inn', 'Frankenmuth, MI 48734'], 'Bavarian Inn team',
    `The Bavarian Inn is one of Frankenmuth's biggest employers, and a lot of our families have worked there or celebrated there. We'd like to carry the Bavarian Inn name on our robot at every event this season.`,
    `We're asking for a Tier One sponsorship of $2,500. That would cover more than a third of the CNC router.`),
  L(["Bronner's CHRISTmas Wonderland", 'Frankenmuth, MI 48734'], "Bronner's team",
    `Bronner's is the reason people from all over the world know where Frankenmuth is. We'd like to take that name to competitions across Michigan.`,
    `We're asking for a Tier One sponsorship of $1,500, which would pay for most of the motors on our drivetrain.`),
  L(['Star of the West Milling Company', 'Frankenmuth, MI 48734'], 'Star of the West team',
    `You know how hard it is to find people who can run equipment, troubleshoot it, and fix it. Our students spend the season doing exactly that: wiring motors and sensors, writing control code, and repairing the robot between matches.`,
    `We're asking for a Tier Two sponsorship of $1,000.`),
  L(['Frankenmuth Woolen Mill', 'Frankenmuth, MI 48734'], 'Woolen Mill team',
    `The Woolen Mill has been making things in Frankenmuth for more than a century. Our students are learning to make things too, from the first sketch to a working machine.`,
    `We're asking for a Tier Two sponsorship of $500.`),
  L(['Frankenmuth Brewery', 'Frankenmuth, MI 48734'], 'Frankenmuth Brewery team',
    `We'd love to have a downtown business on the robot. We'd also be glad to bring it to the Brewery for a family demo night. It draws a crowd.`,
    `We're asking for a Tier Two sponsorship of $500, or the chance to host a team night with you.`),
  L(['Nexteer Automotive', 'Community Relations', 'Saginaw, MI'], 'Nexteer Community Relations team',
    `Nexteer has supported FIRST teams around Saginaw for years, and steering is something our students know well. Our swerve drivetrain steers each wheel independently under software control. Your engineers would recognize the problem right away.`,
    `We're asking for a Tier One sponsorship of $2,500. We'd also be grateful for help finding one or two engineers who could mentor our students through build season.`),
  L(['John Deere', 'Attn: FIRST sponsorship contact'], 'John Deere team',
    `John Deere helped fund our rookie season, and we want you to know what it turned into: our robot qualified for the Michigan State Championship, and we seeded 6th at our Fenton district event. Thank you.`,
    `We'd be grateful if John Deere would renew its support for 2026–27 at Tier One. Your logo would stay on the robot and our shirts, and we'll send updates through the season.`,
    { intro: `We're Complete Robotics, FIRST Robotics Competition Team 10978 from Frankenmuth, Michigan.` }),
  L(['Polymaker', 'Attn: Sponsorship team'], 'Polymaker team',
    `Polymaker sponsored our rookie season. With no machine shop, 3D printing was how we built every custom part, and those parts got us to the Michigan State Championship.`,
    `We're adding a CNC router this year, but we'll still print a lot: prototypes, sensor mounts, brackets and covers. Would Polymaker renew its filament sponsorship for 2026–27? We'd keep your logo on the robot and tag you in our build posts.`,
    { intro: `We're Complete Robotics, FIRST Robotics Competition Team 10978 from Frankenmuth, Michigan.` }),
  ...['Dell Technologies', 'HP', 'Apple', 'Microsoft'].map(co => L(
    [co, 'Community and Education Giving'], `${co} team`,
    `Our students design the robot in CAD, write its code in Java, and this year will program CNC toolpaths too. Right now they share a few old computers that slow to a crawl on a full robot assembly.`,
    `Would ${co} donate four to six laptops that can run CAD, new or refurbished? A grant toward computers would help just as much. We'd recognize ${co} as a Tier One sponsor on the robot, our shirts and our website.`,
    { intro: `We're Complete Robotics, a high school FIRST Robotics Competition team (Team 10978) from Frankenmuth, Michigan.` })),
];

function sponsorLetter(s) {
  return `
<section class="page letter">
  ${letterBrand}
  <div class="date">November 2026</div>
  <div class="to">${s.to.join('<br>')}</div>
  <p>Dear ${s.dear},</p>
  <p>${s.intro || INTRO}</p>
  <p>${s.custom}</p>
  <p>${STORY}</p>
  <p>${s.ask} We're a 501(c)(3), so the gift is tax-deductible.</p>
  <p>${CLOSE}</p>
  <div class="sig">Thank you,</div>
  <div class="sigs"><div>Student, Complete Robotics</div><div>Student, Complete Robotics</div></div>
  <p class="small">${CONTACT.name}, Complete Robotics · ${CONTACT.email}</p>
  ${foot}
</section>`;
}

const zehnders = `
<section class="page letter">
  ${letterBrand}
  <div class="date">November 2026</div>
  <div class="to">Zehnder's of Frankenmuth<br>Attn: Donation Requests<br>730 S. Main Street<br>Frankenmuth, MI 48734</div>
  <p>To the Zehnder's donations committee,</p>
  <p>${PAYEE}, a Frankenmuth 501(c)(3), would like to request a donation of four $50 dining certificates for the raffle at our spring robot showcase in March 2027.</p>
  <p>Complete Robotics is a high school FIRST Robotics Competition team. In our rookie season, students designed and built a robot that qualified for the Michigan State Championship. Raffle proceeds go toward a CNC router so students can machine aluminum parts as we work toward the FIRST Championship.</p>
  <p>This is our only request to Zehnder's this year. We'd recognize Zehnder's on the event signs and in our social media posts.</p>
  <p>Thank you for everything you do for Frankenmuth groups.</p>
  <div class="sigs"><div>${CONTACT.name}, Complete Robotics</div></div>
  <p class="small">${CONTACT.email} · ${CONTACT.web}</p>
  ${foot}
</section>`;

// ---------------- grant letters ----------------
const WHO = `Complete Robotics (FIRST Robotics Competition Team 10978) is a community-based high school team run by ${PAYEE}, a 501(c)(3) in Frankenmuth. In our 2026 rookie season, students designed the entire robot themselves. With no machine shop, they 3D-printed every custom part, and the robot still qualified for the FIRST in Michigan State Championship.`;
function grantLetter(g) {
  return `
<section class="page letter">
  ${letterBrand}
  <div class="date">November 2026</div>
  <div class="to">${g.to.join('<br>')}</div>
  <p>Dear ${g.dear},</p>
  ${g.body.map(t => `<p>${t}</p>`).join('')}
  ${g.table ? `<table class="b">${g.table.map(([k, v]) => `<tr><td>${k}</td><td>${v}</td></tr>`).join('')}</table>` : ''}
  <p>${g.closing}</p>
  <div class="sig">Respectfully,</div>
  <div class="sigs"><div>${CONTACT.name}, Complete Robotics</div><div>Student, Complete Robotics</div></div>
  <p class="small">${CONTACT.email} · ${CONTACT.web}</p>
  <p class="enc">Enclosed: sponsorship sheet, IRS determination letter${g.enc ? ', ' + g.enc : ''}</p>
  ${foot}
</section>`;
}
const grants = [
  { to: ['The Frankenmuth Community Foundation', 'Attn: Executive Director', 'P.O. Box 386', 'Frankenmuth, MI 48734'], dear: 'Members of the Board',
    body: [
      `On behalf of the students of Complete Robotics, I'm requesting a $2,500 grant toward a CNC router for our team workshop.`,
      WHO,
      `This year our goal is the FIRST Championship. A CNC router lets students machine strong, precise aluminum parts instead of printing plastic, and it teaches the same design-to-machine process used by manufacturers across Saginaw County. It will serve every student on the team for years, not just this season.`,
      `The full CNC setup costs about $6,500, and our season budget is ${$(total)}. A $2,500 grant would cover the router or its enclosure and tooling. We'd be glad to recognize the Foundation on the machine and on the robot.`,
    ],
    closing: `Thank you for your support of the greater Frankenmuth area. We'd welcome the chance to bring students and the robot to a board meeting.` },
  { to: ['Frankenmuth Credit Union Foundation for Our Communities', 'Attn: Marketing', 'P.O. Box 209', 'Frankenmuth, MI 48734'], dear: 'Members of the Foundation Board',
    body: [
      `Complete Robotics is requesting a $2,500 grant to buy robot parts and CNC tooling for our 2026–27 season. Our completed application form is enclosed.`,
      WHO,
      `This spring our students will design, machine, wire and program a new robot with one goal: qualify for the FIRST Championship. Your grant would pay for motors, motor controllers and CNC end mills, the parts that turn student designs into a robot that can compete.`,
    ],
    closing: `We'd be glad to present to your board with students and the robot, and to recognize the Foundation on the robot and our team shirts.`, enc: 'FCU grant application form' },
  { to: ['The Herbert H. and Grace A. Dow Foundation', 'Midland, MI 48640'], dear: 'Trustees',
    body: [
      `${PAYEE}, a Michigan 501(c)(3) based in Frankenmuth, is requesting a $10,000 grant. It would give Great Lakes Bay Region high school students a CNC machining setup and the robot parts they need to compete for a spot at the FIRST Championship.`,
      WHO,
      `The region's chemical, semiconductor and manufacturing employers need people who can design, machine and program. Because we're a community team open to students from any school, we can't receive Michigan's school-based 99h robotics grant, so we rely on private support. The grant would fund:`,
    ],
    table: [['CNC router setup: router, stand, enclosure, dust collection and tooling', '$6,500'], ['Robot motors, controllers and electronics', '$3,500'], ['Total request', '$10,000']],
    closing: `We'll report the number of students trained, hours of hands-on machining and programming, competition results, and what our graduates go on to study. We'd welcome the chance to submit a full proposal or bring students and the robot to Midland.` },
];
const cncSummary = `
<section class="page letter">
  ${brand('Project summary')}
  <h2 style="font-size:20pt">CNC workshop project</h2>
  <p class="muted">Attach to applications that ask for a project description or budget: Gene Haas Foundation, Hemlock Semiconductor CARE, Saginaw Community Foundation, Consumers Energy.</p>
  <h3 style="margin-top:14px">The need</h3>
  <p>In our 2026 rookie season, students designed a complete competition robot but had no way to machine metal. Every custom part was 3D-printed. Printed parts take longer to make, break more easily on impact, and limit what students can design. The robot still qualified for the FIRST in Michigan State Championship. A CNC router is the biggest upgrade between us and a FIRST Championship robot.</p>
  <h3>The project</h3>
  <ul class="dots">
    <li>Buy and set up a benchtop CNC router (Omio X8 class, the most common router on FRC teams) with a stand, enclosure and dust collection.</li>
    <li>Train students in the full process: design in CAD, program toolpaths, set up the machine safely, cut parts and check them against the drawing.</li>
    <li>Build our 2027 robot with student-machined aluminum and polycarbonate parts, and offer CNC help to other local teams.</li>
  </ul>
  <h3>Budget</h3>
  <table class="b">
    <tr><td>CNC router, Omio X8-2200L class (WCP lists it at $3,999.99)</td><td>$4,000</td></tr>
    <tr><td>Machine stand and enclosure</td><td>$1,000</td></tr>
    <tr><td>Dust collection</td><td>$400</td></tr>
    <tr><td>End mills, bits, collets and workholding</td><td>$700</td></tr>
    <tr><td>Spoilboard, safety equipment, wiring and setup</td><td>$400</td></tr>
    <tr><td>Total</td><td>$6,500</td></tr>
  </table>
  <h3>What we'll report</h3>
  <ul class="dots">
    <li>Students trained on the CNC (goal: everyone on the build team)</li>
    <li>Share of custom robot parts machined in-house (goal: most structural parts)</li>
    <li>Competition results, including whether we qualify for the FIRST Championship</li>
    <li>Students going on to engineering, manufacturing or skilled-trades programs</li>
  </ul>
  ${foot}
</section>`;

const wrap = (title, body) => `<!doctype html><html><head><meta charset="utf-8"><title>${title}</title><style>${css}</style></head><body>${body}</body></html>`;

(async () => {
  const files = {
    'Complete_Robotics_Sponsorship_Packet.pdf': wrap('Complete Robotics Sponsorship', packet()),
    'Complete_Robotics_Sponsor_Letters.pdf': wrap('Sponsor Letters', sponsors.map(sponsorLetter).join('') + zehnders),
    'Complete_Robotics_Grant_Letters.pdf': wrap('Grant Letters', grants.map(grantLetter).join('') + cncSummary),
  };
  const browser = await chromium.launch();
  const page = await browser.newPage();
  for (const [name, html] of Object.entries(files)) {
    const htmlPath = path.join(__dirname, name.replace('.pdf', '.html'));
    fs.writeFileSync(htmlPath, html);
    await page.goto('file://' + htmlPath, { waitUntil: 'load' });
    await page.pdf({ path: path.join(OUT, name), format: 'Letter', printBackground: true, preferCSSPageSize: true });
    console.log('wrote', name);
  }
  await browser.close();
})();
