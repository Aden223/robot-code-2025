// Builds the Complete Robotics fundraising PDFs from HTML.
// Usage: node build.js   (run from this folder; needs Playwright + the Inter font)
// Photos live in ../photos.
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const OUT = path.resolve(__dirname, '..');
const PHOTOS = path.resolve(__dirname, '..', 'photos');
const img = (f) => `file://${PHOTOS}/${f}`;

const CONTACT = {
  name: 'Sarah Rice',
  email: 'SarahRice@CompleteWorship.org',
  web: 'CompleteWorship.org/programs/stem',
  fb: 'Complete Robotics',
};
// Legal payee for checks. Leave null until Sarah confirms (Complete Worship or Complete Robotics Foundation).
const PAYEE = null;
const PAY_TEXT = PAYEE ? `Make checks payable to ${PAYEE}.` : `Contact ${CONTACT.name} for check and payment details.`;
const DATE = 'November 2026';

const BUDGET = [
  ['CNC router, enclosure and tooling', 6500],
  ['Motors, motor controllers and electronics', 8000],
  ['Aluminum, polycarbonate and filament', 3000],
  ['FIRST registration and district events', 6000],
  ['State Championship', 1500],
  ['FIRST Championship fund', 5000],
  ['Safety gear, tools and outreach', 1000],
];
const TOTAL = BUDGET.reduce((a, [, v]) => a + v, 0);
const $ = (n) => '$' + n.toLocaleString('en-US');

const css = `
@page { size: Letter; margin: 0; }
* { box-sizing: border-box; margin: 0; padding: 0; }
:root { --ink:#1b211d; --muted:#5b665f; --line:#d9e1dc; --green:#1f6b42; --dark:#15462c; --tint:#eef6f1; }
html { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
body { font-family: Inter, sans-serif; color: var(--ink); font-size: 10.5pt; line-height: 1.55; }
.page { width: 8.5in; height: 11in; padding: 0.65in 0.75in 0.8in; position: relative; overflow: hidden; page-break-after: always; }
.page:last-child { page-break-after: auto; }
h1 { font-family: 'Inter Display', Inter; font-weight: 700; font-size: 32pt; line-height: 1.05; letter-spacing: -0.02em; }
h2 { font-family: 'Inter Display', Inter; font-weight: 650; font-size: 13.5pt; color: var(--green); margin: 0 0 6px; }
h3 { font-size: 10.5pt; font-weight: 650; margin-bottom: 3px; }
p { margin-bottom: 9px; }
.muted { color: var(--muted); }
.small { font-size: 9pt; }
.rule { border-top: 1px solid var(--line); margin: 16px 0; }

.top { display: flex; justify-content: space-between; align-items: baseline; font-size: 8.5pt; color: var(--muted);
  border-bottom: 2px solid var(--green); padding-bottom: 8px; margin-bottom: 22px; }
.top b { color: var(--ink); font-size: 10pt; }
.sub { font-size: 12pt; color: var(--muted); margin: 6px 0 18px; }
.cover { width: 100%; height: 2.8in; object-fit: cover; object-position: center 64%; border-radius: 10px; display: block; }
.cap { font-size: 8pt; color: var(--muted); margin: 5px 0 18px; }
.cols { display: grid; grid-template-columns: 1fr 1fr; gap: 28px; }
ul.list { list-style: none; margin: 2px 0 10px; }
ul.list li { position: relative; padding-left: 15px; margin-bottom: 4px; }
ul.list li::before { content: ''; position: absolute; left: 0; top: 7px; width: 6px; height: 6px; border-radius: 50%; background: var(--green); }

.photos { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 10px 0 4px; }
.photos img { width: 100%; height: 1.55in; object-fit: cover; border-radius: 8px; display: block; }
.photos div { font-size: 8pt; color: var(--muted); margin-top: 4px; }

table.b { width: 100%; border-collapse: collapse; font-size: 10pt; margin: 4px 0 6px; }
table.b td { padding: 6px 0; border-bottom: 1px solid var(--line); }
table.b td:last-child { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; padding-left: 12px; }
table.b tr.total td { border-bottom: 0; border-top: 2px solid var(--green); font-weight: 650; padding-top: 8px; }

.tiers { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 8px 0 10px; }
.tier { border: 1px solid var(--line); border-top: 4px solid var(--green); border-radius: 8px; padding: 12px 14px 12px; }
.tier.one { background: var(--tint); }
.tier .amt { font-family: 'Inter Display', Inter; font-size: 17pt; font-weight: 700; color: var(--dark); margin: 2px 0 8px; }
.tier li { font-size: 9.5pt; margin-bottom: 3px; }

.form { border: 1px solid var(--line); border-radius: 10px; padding: 16px 18px 6px; margin-top: 8px; }
.field { display: flex; align-items: flex-end; gap: 10px; margin-bottom: 14px; font-size: 9.5pt; }
.field span { white-space: nowrap; }
.field i { flex: 1; border-bottom: 1px solid #8f9b94; height: 1px; }
.row { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
.check { display: flex; gap: 22px; flex-wrap: wrap; margin: 2px 0 14px; font-size: 9.5pt; }
.box { display: inline-block; width: 11px; height: 11px; border: 1px solid #6c786f; border-radius: 2px; margin-right: 6px; vertical-align: -1px; }

/* letters */
.lh { display: flex; justify-content: space-between; align-items: flex-end; border-bottom: 2px solid var(--green); padding-bottom: 10px; margin-bottom: 30px; }
.lh .name { font-family: 'Inter Display', Inter; font-weight: 700; font-size: 17pt; line-height: 1.1; }
.lh .tag { font-size: 8.5pt; color: var(--muted); }
.lh .right { text-align: right; font-size: 8.5pt; color: var(--muted); line-height: 1.5; }
.letter { font-size: 10.5pt; line-height: 1.6; }
.letter .to { margin: 18px 0 20px; line-height: 1.45; }
.letter p { margin-bottom: 12px; }
.sign { margin-top: 30px; line-height: 1.45; }
.enc { margin-top: 18px; font-size: 9pt; color: var(--muted); }
.foot { position: absolute; left: 0.75in; right: 0.75in; bottom: 0.45in; font-size: 8pt; color: var(--muted); border-top: 1px solid var(--line); padding-top: 7px; display: flex; justify-content: space-between; }
`;

const top = `<div class="top"><span><b>Complete Robotics</b> &nbsp;·&nbsp; FIRST Robotics Competition Team 10978 &nbsp;·&nbsp; Frankenmuth, Michigan</span><span>2026–2027 Sponsorship</span></div>`;
const foot = `<div class="foot"><span>Gifts to Complete Robotics are tax-deductible.</span><span>${CONTACT.email} &nbsp;·&nbsp; Facebook: ${CONTACT.fb}</span></div>`;

// ---------------- packet ----------------
const packet = `
<section class="page">
  ${top}
  <h1>Complete Robotics</h1>
  <div class="sub">FIRST Robotics Competition Team 10978 &nbsp;·&nbsp; 2026–2027 Sponsorship Packet</div>
  <img class="cover" src="${img('robot_on_field.jpg')}">
  <div class="cap">Our robot competing during the 2025–26 season.</div>
  <div class="cols">
    <div>
      <h2>About us</h2>
      <p>Complete Robotics is a community robotics team based in Frankenmuth. Our students are in grades 7 through 12 and come from public, private, virtual and home schools and the career center in Frankenmuth, Birch Run, Saginaw and Bay City. No experience is needed to join.</p>
      <p>The team is part of Complete Worship's youth programs, which began in 2019 and added STEM programs in 2024.</p>
    </div>
    <div>
      <h2>About FIRST</h2>
      <p>FIRST Robotics Competition is an international program for high school students. Each January, teams get a new game and about eight weeks to design, build and program a robot of up to 125 pounds to play it. More than 93,000 students in 35 countries took part in the 2025 season.</p>
    </div>
  </div>
  <div class="rule"></div>
  <div class="cols">
    <div>
      <h2>Our first season, 2025–26</h2>
      <ul class="list">
        <li>Reached the finals at both of our district events</li>
        <li>Won the Rookie All Star Award</li>
        <li>Qualified for and competed in the FIRST in Michigan State Championship</li>
      </ul>
    </div>
    <div>
      <h2>What students do</h2>
      <p>Students work in coding, CAD, building, machining, wiring, 3D printing, driving, scouting, media and marketing. Mentors with backgrounds in engineering, machining, electronics and programming guide them.</p>
    </div>
  </div>
  ${foot}
</section>

<section class="page">
  ${top}
  <h2>Our 2026 robot</h2>
  <p>Students designed the robot in CAD and programmed it in Java. It drove on a swerve drivetrain, where each wheel steers on its own, and launched balls with a flywheel shooter. We didn't have a way to machine metal, so we 3D printed many of the custom parts, including gears and the shooter frame.</p>
  <div class="photos">
    <figure><img src="${img('printed_shooter.jpg')}"><div>3D-printed shooter assembly</div></figure>
    <figure><img src="${img('printed_gears.jpg')}"><div>3D-printed gears on a Kraken X44 motor</div></figure>
    <figure><img src="${img('cardboard_prototype.jpg')}"><div>An early prototype with a cardboard hopper</div></figure>
  </div>
  <div class="rule"></div>
  <h2>This season's goal</h2>
  <p>This season we want to qualify for the FIRST Championship in Houston. Our biggest need is a CNC router so students can cut parts from aluminum instead of printing them. Aluminum parts are stronger and more precise, and the machine will serve the team for years.</p>
  <div class="cols" style="grid-template-columns: 1.15fr 1fr; margin-top:6px">
    <div>
      <h3>Estimated 2026–27 budget</h3>
      <table class="b">
        ${BUDGET.map(([k, v]) => `<tr><td>${k}</td><td>${$(v)}</td></tr>`).join('')}
        <tr class="total"><td>Total</td><td>${$(TOTAL)}</td></tr>
      </table>
    </div>
    <div>
      <h3>Notes</h3>
      <p class="small">Parts are the largest cost. Motors and electronics wear out or break during a season, and a competitive robot needs spares.</p>
      <p class="small">The FIRST Championship fund covers registration and a travel deposit. If we qualify, we will raise travel money separately.</p>
      <p class="small">Every sponsor receives a report at the end of the season showing how the money was spent.</p>
    </div>
  </div>
  ${foot}
</section>

<section class="page">
  ${top}
  <h2>Sponsorship levels</h2>
  <p>Sponsors are recognized for the full season, from January through our last event in the spring. Donated materials, equipment and services count toward a level at their full value.</p>
  <div class="tiers">
    <div class="tier one"><h3>Tier One</h3><div class="amt">$1,500+</div><ul class="list">
      <li>Logo on the robot</li><li>Logo on team shirts</li><li>Logo on our website</li><li>Logo in our pit at competitions</li><li>Two social media posts</li></ul></div>
    <div class="tier"><h3>Tier Two</h3><div class="amt">$500–$1,499</div><ul class="list">
      <li>Logo on team shirts</li><li>Logo on our website</li><li>Logo in our pit at competitions</li><li>One social media post</li></ul></div>
    <div class="tier"><h3>Tier Three</h3><div class="amt">$100–$499</div><ul class="list">
      <li>Name on team shirts</li><li>Name on our website</li><li>Name in a social media post</li></ul></div>
  </div>
  <p class="small muted">We are also looking for mentors. Many employers match their employees' volunteer hours with a donation.</p>
  <h2 style="margin-top:14px">Sponsorship form</h2>
  <p class="small">Please return this form to ${CONTACT.name} at ${CONTACT.email}. ${PAY_TEXT} For logo recognition, please email a high-resolution logo.</p>
  <div class="form">
    <div class="field"><span>Company or organization</span><i></i></div>
    <div class="row"><div class="field"><span>Contact name</span><i></i></div><div class="field"><span>Title</span><i></i></div></div>
    <div class="row"><div class="field"><span>Phone</span><i></i></div><div class="field"><span>Email</span><i></i></div></div>
    <div class="field"><span>Mailing address</span><i></i></div>
    <div class="check"><span><span class="box"></span>Tier One ($1,500+)</span><span><span class="box"></span>Tier Two ($500–$1,499)</span><span><span class="box"></span>Tier Three ($100–$499)</span></div>
    <div class="row"><div class="field"><span>Amount $</span><i></i></div><div class="field"><span>In-kind gift</span><i></i></div></div>
    <div class="field"><span>Name as it should appear on shirts and website</span><i></i></div>
  </div>
  ${foot}
</section>`;

// ---------------- letters ----------------
const letterhead = `
<div class="lh">
  <div><div class="name">Complete Robotics</div><div class="tag">FIRST Robotics Competition Team 10978 · Frankenmuth, Michigan</div></div>
  <div class="right">${CONTACT.email}<br>${CONTACT.web}</div>
</div>`;

const INTRO = `I'm writing on behalf of Complete Robotics, a community FIRST Robotics Competition team (Team 10978) based here in Frankenmuth. Our students are in grades 7 through 12 and come from Frankenmuth, Birch Run, Saginaw and Bay City.`;
const SEASON = `Last season was our first. The students designed, built and programmed their own robot, reached the finals at both of our district events, won the Rookie All Star Award and qualified for the Michigan State Championship.`;
const GOAL = `This season the team wants to qualify for the FIRST Championship in Houston. We are raising about ${$(TOTAL)} for a CNC router, robot parts and event fees. Last year we 3D printed many of our custom parts because we had no way to machine metal, so the router is our top priority.`;
const PACKET = `I've enclosed our sponsorship packet, which lists our sponsorship levels and how sponsors are recognized.`;
const FOLLOW = `I'll follow up by phone in the next week or so. We would also be glad to bring the robot by so you can see it in person.`;

function letter({ to, dear, body, enc = 'Sponsorship packet' }) {
  return `
<section class="page letter">
  ${letterhead}
  <div>${DATE}</div>
  <div class="to">${to.join('<br>')}</div>
  <p>Dear ${dear},</p>
  ${body.map((t) => `<p>${t}</p>`).join('')}
  <p>Thank you for considering our request.</p>
  <div class="sign">Sincerely,<br><br><br>${CONTACT.name}<br>Complete Robotics, FRC Team 10978<br>${CONTACT.email}</div>
  ${enc ? `<div class="enc">Enclosure: ${enc}</div>` : ''}
</section>`;
}

const sponsorLetters = [
  { to: ['Mr. Mike Grossi', 'President, Kremin Inc.', '235 Keystone Way', 'Frankenmuth, MI 48734'], dear: 'Mr. Grossi',
    body: [INTRO, SEASON,
      `This season we are raising money for a CNC router. Last year we 3D printed many of our custom parts because we had no way to machine metal, and we want students to learn to make those parts from aluminum. Since Kremin does precision machining right here in town, we would be grateful for your help in whatever form works for you: a sponsorship, machine time for a few parts, or a visit where students could see how a professional shop runs.`,
      PACKET + ' ' + FOLLOW] },
  { to: ['Frankenmuth Bavarian Inn', 'Frankenmuth, MI 48734'], dear: 'Friends at the Bavarian Inn',
    body: [INTRO, SEASON, GOAL,
      `Would the Bavarian Inn consider sponsoring us at the Tier One level ($1,500 or more)? Your name would be on the robot, our team shirts and our pit display at every event this season. ${PACKET}`, FOLLOW] },
  { to: ["Bronner's CHRISTmas Wonderland", 'Frankenmuth, MI 48734'], dear: "Friends at Bronner's",
    body: [INTRO, SEASON, GOAL,
      `Would Bronner's consider sponsoring us at the Tier One level ($1,500 or more)? Your name would travel with the robot to every event this season. ${PACKET}`, FOLLOW] },
  { to: ['Star of the West Milling Company', 'Frankenmuth, MI 48734'], dear: 'Friends at Star of the West',
    body: [INTRO, SEASON, GOAL,
      `Our students spend the season wiring motors and sensors, writing control code and repairing the robot between matches. Those are the same skills local manufacturers look for. Would Star of the West consider sponsoring us at the Tier Two level ($500 to $1,499)? ${PACKET}`, FOLLOW] },
  { to: ['Frankenmuth Woolen Mill', 'Frankenmuth, MI 48734'], dear: 'Friends at the Frankenmuth Woolen Mill',
    body: [INTRO, SEASON, GOAL,
      `Would the Woolen Mill consider sponsoring us at the Tier Two level ($500 to $1,499)? ${PACKET}`, FOLLOW] },
  { to: ['Frankenmuth Brewery', 'Frankenmuth, MI 48734'], dear: 'Friends at Frankenmuth Brewery',
    body: [INTRO, SEASON, GOAL,
      `Would Frankenmuth Brewery consider sponsoring us at the Tier Two level ($500 to $1,499)? We would also be happy to bring the robot downtown for a demonstration. ${PACKET}`, FOLLOW] },
  { to: ['Community Relations', 'Nexteer Automotive', 'Saginaw, MI'], dear: 'Nexteer Community Relations Team',
    body: [INTRO, SEASON, GOAL,
      `Nexteer has supported FIRST teams in the Saginaw area for many years, and our students would benefit from that experience. We are asking Nexteer to consider sponsoring us at the Tier One level ($1,500 or more). We would also welcome one or two Nexteer engineers as volunteer mentors during the build season. ${PACKET}`, FOLLOW] },
  { to: ['John Deere', 'Attn: FIRST sponsorship contact'], dear: 'John Deere Team', enc: '',
    body: [
      `Thank you for supporting Complete Robotics, FIRST Robotics Competition Team 10978 from Frankenmuth, Michigan, during our rookie season. I wanted to let you know how that season went.`,
      `With your help, our students designed, built and programmed their first robot. They reached the finals at both of our district events, won the Rookie All Star Award and qualified for the Michigan State Championship.`,
      `This season the team wants to qualify for the FIRST Championship in Houston, and we hope to continue working with John Deere. Your logo will remain on our robot and team shirts, and we will send updates throughout the season.`] },
  { to: ['Polymaker', 'Attn: Sponsorship team'], dear: 'Polymaker Team', enc: '',
    body: [
      `Thank you for sponsoring Complete Robotics, FIRST Robotics Competition Team 10978 from Frankenmuth, Michigan, during our rookie season.`,
      `Your filament went into many of the parts on our first robot, including gears and the shooter frame. That robot reached the finals at both of our district events, won the Rookie All Star Award and qualified for the Michigan State Championship.`,
      `This season we are adding a CNC router, but we will still print prototypes, brackets and covers. We would be grateful if Polymaker would continue its filament sponsorship for the 2026–27 season. We will keep your logo on the robot and tag you in our build-season posts.`] },
];

// ---------------- grant letters ----------------
const WHO = `Complete Robotics (FIRST Robotics Competition Team 10978) is a community team for students in grades 7 through 12, run as part of Complete Worship's youth programs in Frankenmuth. Our students come from public, private, virtual and home schools in Frankenmuth, Birch Run, Saginaw and Bay City. In our first season, 2025–26, they reached the finals at both of our district events, won the Rookie All Star Award and qualified for the Michigan State Championship.`;
const NEED = `Last season we had no way to machine metal, so students 3D printed many of the robot's custom parts. A CNC router would let them cut those parts from aluminum and learn the same process that local manufacturers use. The machine would serve the team for many seasons.`;

const grantLetters = [
  { to: ['The Frankenmuth Community Foundation', 'Attn: Executive Director', 'P.O. Box 386', 'Frankenmuth, MI 48734'], dear: 'Members of the Board', enc: 'Sponsorship packet; IRS determination letter',
    body: [
      `On behalf of Complete Robotics, I am requesting a grant of $2,500 to help purchase a CNC router for our team workshop.`,
      WHO, NEED,
      `The router, enclosure and tooling will cost about $6,500. Your grant would cover more than a third of that cost, and we are seeking the balance from other local funders and sponsors. We would be glad to bring students and the robot to a board meeting.`] },
  { to: ['Frankenmuth Credit Union Foundation for Our Communities', 'Attn: Marketing', 'P.O. Box 209', 'Frankenmuth, MI 48734'], dear: 'Members of the Foundation Board', enc: 'Grant application form; sponsorship packet; IRS determination letter',
    body: [
      `On behalf of Complete Robotics, I am requesting a grant of $2,500 for robot parts and CNC tooling for our 2026–27 season. Our completed application form is enclosed.`,
      WHO,
      `This season the students want to qualify for the FIRST Championship. Your grant would pay for motors, motor controllers and cutting tools for our new CNC router. Our total budget for the season is about ${$(TOTAL)}.`,
      `We would be glad to present to the board with students and the robot, and to recognize the Foundation on our robot and team shirts.`] },
  { to: ['The Herbert H. and Grace A. Dow Foundation', 'Midland, MI 48640'], dear: 'Trustees', enc: 'Sponsorship packet; IRS determination letter; CNC project summary',
    body: [
      `On behalf of Complete Robotics, I am requesting a grant of $10,000 to equip our team with a CNC router and the robot parts students need for the 2026–27 season.`,
      WHO, NEED,
      `The grant would fund the CNC router, enclosure and tooling ($6,500) and motors, controllers and electronics ($3,500). As a community team, we are not eligible for Michigan's school-based robotics grant, so we rely on private support.`,
      `We will report the number of students trained, competition results and the cost of every purchase. We would welcome the chance to submit a full proposal or to bring students and the robot to Midland.`] },
];

const cncSummary = `
<section class="page letter">
  ${letterhead}
  <h1 style="font-size:20pt; margin-bottom:6px">CNC Router Project Summary</h1>
  <p class="muted small">Complete Robotics, FRC Team 10978 &nbsp;·&nbsp; For use with grant applications</p>
  <h2 style="margin-top:14px">Need</h2>
  <p>In our 2025–26 rookie season, students designed and built a competition robot that reached the finals at both of our district events and qualified for the Michigan State Championship. Without a way to machine metal, they 3D printed many of the custom parts. Printed parts take longer to make, wear faster and limit what students can design. Our mentors have machining experience; what we need is the machine.</p>
  <h2>Project</h2>
  <ul class="list">
    <li>Purchase and install a benchtop CNC router (Omio X8 class, a common choice for FIRST teams) with a stand, enclosure and dust collection.</li>
    <li>Train students to design parts in CAD, create toolpaths, set up the machine safely, cut parts and check them against the drawing.</li>
    <li>Build the 2027 robot with student-machined aluminum and polycarbonate parts.</li>
  </ul>
  <h2>Budget (estimated)</h2>
  <table class="b">
    <tr><td>CNC router, Omio X8-2200L class (WCP lists it at $3,999.99)</td><td>$4,000</td></tr>
    <tr><td>Machine stand and enclosure</td><td>$1,000</td></tr>
    <tr><td>Dust collection</td><td>$400</td></tr>
    <tr><td>End mills, collets and workholding</td><td>$700</td></tr>
    <tr><td>Spoilboard, safety equipment, wiring and setup</td><td>$400</td></tr>
    <tr class="total"><td>Total</td><td>$6,500</td></tr>
  </table>
  <h2 style="margin-top:12px">What we will report</h2>
  <ul class="list">
    <li>Number of students trained on the machine</li>
    <li>Share of the robot's custom parts made in-house</li>
    <li>Competition results for the 2026–27 season</li>
    <li>Students who go on to study engineering, manufacturing or the skilled trades</li>
  </ul>
</section>`;

const wrap = (title, body) => `<!doctype html><html><head><meta charset="utf-8"><title>${title}</title><style>${css}</style></head><body>${body}</body></html>`;

(async () => {
  const files = {
    'Complete_Robotics_Sponsorship_Packet.pdf': wrap('Complete Robotics Sponsorship Packet', packet),
    'Complete_Robotics_Sponsor_Letters.pdf': wrap('Complete Robotics Sponsor Letters', sponsorLetters.map(letter).join('')),
    'Complete_Robotics_Grant_Letters.pdf': wrap('Complete Robotics Grant Letters', grantLetters.map(letter).join('') + cncSummary),
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
