import { genericBlockMatter } from "../../util";

const BASE = new URL("emc_link_nofilter.json", import.meta.url);
export default genericBlockMatter.bind(null, "emc_link", BASE, [], "_nofilter");
