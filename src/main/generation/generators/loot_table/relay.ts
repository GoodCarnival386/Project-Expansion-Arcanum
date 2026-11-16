import { genericMatter } from "../../util";

const BASE = new URL("relay.json", import.meta.url);
export default genericMatter.bind(null, "relay", BASE, []);
