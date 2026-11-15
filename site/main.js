// IMD TV site: builds the section rail, highlights the section in view, reveals each section once. No tracking.
(function () {
  document.documentElement.classList.remove('no-js');
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
