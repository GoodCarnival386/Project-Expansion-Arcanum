import { genericMatter } from "../../util";

const BASE = new URL("matter.json", import.meta.url);
export default genericMatter.bind(null, "matter", BASE, []);
