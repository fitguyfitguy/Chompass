(function () {
  "use strict";

  var visible = false;
  var buf = "";
  var target = "chomp ass";

  function chompAss() {
    if (typeof console !== "undefined" && console.log) {
      console.log(
        "%cChomp ass.%c\n" +
          "That's the brand: eat well, stay private, leave no crumbs.\n" +
          "Calorie estimate for this joke: classified.",
        "font-family:Georgia,'Iowan Old Style',serif;font-size:1.75rem;font-weight:700;color:#5cc48f;",
        "font-family:system-ui,sans-serif;font-size:0.85rem;color:#a9a4ad;line-height:1.5;"
      );
    }
    showToast();
    return "🍑 Ass chomped. You're in on the joke.";
  }

  function showToast() {
    if (visible || !document.body) return;
    visible = true;
    var el = document.createElement("div");
    el.className = "site-toast";
    el.setAttribute("role", "status");
    el.innerHTML =
      "<strong>Chomp ass.</strong>" +
      "<span>You found the name. Discretion advised.</span>";
    document.body.appendChild(el);
    requestAnimationFrame(function () {
      el.classList.add("site-toast--in");
    });
    window.setTimeout(function () {
      el.classList.remove("site-toast--in");
      window.setTimeout(function () {
        el.remove();
        visible = false;
      }, 380);
    }, 4200);
  }

  document.addEventListener(
    "keydown",
    function (e) {
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      var t = e.target;
      if (
        t &&
        (t.isContentEditable ||
          /^(INPUT|TEXTAREA|SELECT)$/i.test(t.tagName || ""))
      ) {
        return;
      }
      if (!e.key || e.key.length !== 1) return;
      buf = (buf + e.key.toLowerCase()).slice(-target.length);
      if (buf === target) {
        buf = "";
        chompAss();
      }
    },
    true
  );

  window.chompAss = chompAss;
  window.chomp = { ass: chompAss };

  if (typeof console !== "undefined" && console.info) {
    console.info(
      "%cHungry?%c Run %cchompAss()%c — or type it on the page.",
      "color:#5cc48f;font-weight:700",
      "color:#a9a4ad",
      "font-family:ui-monospace,Menlo,monospace;color:#e6e1e5;background:#24232a;padding:0.1em 0.4em;border-radius:0.3em",
      "color:#a9a4ad"
    );
  }
})();
