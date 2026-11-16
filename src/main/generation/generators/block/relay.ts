import { genericBlockMatter } from "../../util";

const BASE = new URL("relay.json", import.meta.url);
export default genericBlockMatter.bind(null, "relay", BASE, [], undefined);
