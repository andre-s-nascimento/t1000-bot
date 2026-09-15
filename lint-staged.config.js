module.exports = {
  '*.java': () => './gradlew spotlessApply --no-configuration-cache',
  '*.{json,md,yml}': 'prettier --write',
};