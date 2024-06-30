global.XMLHttpRequest = require('xhr2');
const { GenericContainer, Wait } = require("testcontainers");
const os = require("os-utils");

describe("EasyRacer", () => {
  let container;

  beforeAll(async () => {
    container = await new GenericContainer("ghcr.io/jamesward/easyracer")
      .withExposedPorts(8080)
      .withWaitStrategy(Wait.forHttp("/", 8080))
      .start();
  }, 30_000);

  afterAll(async () => {
    await container.stop();
  });

  for (const idx of Array(9).keys()) {
    const scenarioNum = idx + 1
    it("scenario " + scenarioNum, async () => {
      const name = "Scenario" + scenarioNum
      const { Elm } = require("../app/EasyRacer/" + name);
      const scenario = Elm.EasyRacer[name].init({
        flags: `http://${container.getHost()}:${container.getMappedPort(8080)}`
      });
      // For scenario 10
      scenario.ports.requestCpuLoadPercent.subscribe(function() {
        os.cpuUsage(function(cpuLoadPercent){
          scenario.ports.receiveCpuLoadPercent.send(cpuLoadPercent);
        });
      });
      let resultPromise = new Promise((resolve, reject) => {
        scenario.ports.sendResult_.subscribe(function(scenarioResult) {
          if (scenarioResult.isError) {
            reject(new Error(scenarioResult.value));
          } else {
            resolve(scenarioResult.value);
          }
        });
      });

      expect(await resultPromise).toBe("right");
    }, 30_000);
  }
});
