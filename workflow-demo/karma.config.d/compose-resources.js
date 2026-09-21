// Compose fetches resources relative to the page, so serve the test bundle's copy at that path.
config.files.push({
  pattern: "kotlin/composeResources/**/*",
  included: false,
  served: true,
  watched: false,
});
config.proxies["/composeResources/"] = "/base/kotlin/composeResources/";
