// IMD TV site: builds the section rail, highlights the section in view, reveals each section once. No tracking.
(function () {
  document.documentElement.classList.remove('no-js');

  // Eased wheel scrolling: each wheel step moves a target and the page glides to it, slowing as it arrives.
  // Touch, keyboard, scrollbar and anchor scrolling stay native; off for people who ask for reduced motion.
  if (!matchMedia('(prefers-reduced-motion: reduce)').matches) {
    var target = 0, current = 0, gliding = false;
    function glide() {
      current += (target - current) * 0.1;
      if (Math.abs(target - current) < 0.5) { current = target; gliding = false; }
      window.scrollTo({ top: current, behavior: 'instant' });
      if (gliding) requestAnimationFrame(glide);
    }
    window.addEventListener('wheel', function (e) {
      if (e.ctrlKey || Math.abs(e.deltaX) > Math.abs(e.deltaY)) return; // pinch-zoom and sideways scrolls
      if (e.deltaMode === 0 && Math.abs(e.deltaY) < 50) return; // touchpads send small steps and already glide
      e.preventDefault();
      if (!gliding) target = current = window.scrollY; // pick up wherever keyboard or links left the page
      var max = document.documentElement.scrollHeight - innerHeight;
      target = Math.max(0, Math.min(max, target + e.deltaY * (e.deltaMode === 1 ? 16 : 1)));
      if (!gliding) { gliding = true; requestAnimationFrame(glide); }
    }, { passive: false });
  }
  var sections = Array.prototype.slice.call(document.querySelectorAll('main section[data-name]'));
  var rail = document.querySelector('.rail');
  var links = sections.map(function (s) {
    var a = document.createElement('a');
    a.href = '#' + s.id;
    a.innerHTML = '<span class="line" aria-hidden="true"></span><span></span>';
    a.lastChild.textContent = s.dataset.name;
    rail.appendChild(a);
    return a;
  });

  function mark(target) {
    links.forEach(function (l, i) {
      var on = sections[i] === target;
      l.classList.toggle('on', on);
      if (on) l.setAttribute('aria-current', 'location'); else l.removeAttribute('aria-current');
    });
  }

  if (!('IntersectionObserver' in window)) {
    sections.forEach(function (s) { var r = s.querySelector('.reveal'); if (r) r.classList.add('in'); });
    return;
  }
  // Reveal once, as soon as any part of a section is a little way into the screen (a fraction would never be reached
  // by a section taller than the screen several times over, e.g. on a phone or when zoomed); the rail follows the
  // section crossing the middle of the screen.
  var reveal = new IntersectionObserver(function (entries) {
    entries.forEach(function (e) {
      if (!e.isIntersecting) return;
      var r = e.target.querySelector('.reveal');
      if (r) r.classList.add('in');
      reveal.unobserve(e.target);
    });
  }, { threshold: 0, rootMargin: '0px 0px -12% 0px' });
  var spy = new IntersectionObserver(function (entries) {
    entries.forEach(function (e) { if (e.isIntersecting) mark(e.target); });
  }, { rootMargin: '-50% 0px -50% 0px' });
  sections.forEach(function (s) { reveal.observe(s); spy.observe(s); });

  var top = document.querySelector('.top');
  function onScroll() { top.classList.toggle('scrolled', window.scrollY > 40); }
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();
})();
