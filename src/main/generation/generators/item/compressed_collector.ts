import { genericMatter } from "../../util";

const BASE = new URL("collector.json", import.meta.url);
export default genericMatter.bind(null, "compressed_collector", BASE, []);
