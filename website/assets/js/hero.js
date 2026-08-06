(function () {
  "use strict";

  // Live hero: swap the 6 MB usage mp4 for the real PWA demo iframe when the
  // hero approaches the viewport. The video stays as the no-JS fallback with
  // preload="none" (poster only) so modern browsers never download it.
  var hero = document.querySelector("[data-live-hero]");
  if (!hero) return;
  var video = hero.querySelector("video");
  var tpl = hero.querySelector("template");
  if (!video || !tpl) return;
  if (!("IntersectionObserver" in window)) return;

  var iframe = null;

  function postToHero(type) {
    if (iframe && iframe.contentWindow) {
      try {
        iframe.contentWindow.postMessage(
          { source: "chompass-hero", type: type },
          window.location.origin,
        );
      } catch (e) {
        /* ignore */
      }
    }
  }

  var lazy = new IntersectionObserver(
    function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        lazy.disconnect();
        var frame = document.createElement("div");
        frame.className = "hero-phone";
        frame.innerHTML = tpl.innerHTML;
        iframe = frame.querySelector("iframe");
        video.replaceWith(frame);
        if (iframe) {
          iframe.addEventListener(
            "load",
            function () {
              frame.classList.add("hero-phone--ready");
              postToHero("play");
            },
            { once: true },
          );
        }
      });
    },
    { rootMargin: "900px" },
  );
  lazy.observe(hero);

  // Pause the demo while scrolled away or backgrounded; resume when back.
  var active = new IntersectionObserver(
    function (entries) {
      entries.forEach(function (entry) {
        postToHero(entry.isIntersecting ? "play" : "pause");
      });
    },
    { threshold: 0 },
  );
  active.observe(hero);

  document.addEventListener("visibilitychange", function () {
    if (iframe) postToHero(document.hidden ? "pause" : "play");
  });
})();
