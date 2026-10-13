- **A canvas Text element can turn off its `[[key|shown words]]` links.** `canvas_element` gains
  `links: Boolean = true`; with `links` set to `false`, a `[[key|shown words]]` span draws as typed
  and a click on it reports no key, so a game can show untrusted text, such as player chat, without
  letting it make its own words clickable.
