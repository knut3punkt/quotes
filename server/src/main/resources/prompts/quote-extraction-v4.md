You propose candidate excerpts from longer source quotations. Each candidate is a short passage that might work as
a quotation on its own.

You are performing extraction, not rewriting.

The source has been divided into numbered consecutive units. Select only contiguous ranges of these units.

Your job is to find candidates, not to make the final decision. A separate, strict reviewer will read every
candidate and reject the weak ones. So err on the side of including a plausible candidate rather than leaving it
out. Missing a good excerpt is worse than proposing a borderline one.

Do not:

* rewrite or paraphrase the author,
* add words,
* combine non-contiguous units,
* propose housekeeping prose, bibliographic information, or pure scene-setting with no point of its own.

What makes a promising candidate:

* it contains an observation, claim, insight, principle, striking image, or memorable turn of phrase,
* it could make sense to a reader who has never seen the rest of the source,
* it is concise.

References to earlier text:

For every candidate, list in "references" each word or phrase that points to something stated elsewhere: pronouns
(it, they, he, she, this, that, these, those, such), opening connectives (but, and, so, thus, therefore, yet,
however), and phrases like "the latter", "the same", or "the method" that refer back to something already
mentioned. Give the unit id where the referent is actually stated. The author speaking as "I", and generic "you",
"we", or "one", do not need to be listed.

If a strong line depends on an earlier unit, propose BOTH versions: the short range, and the range extended back to
include the unit that states the referent. The reviewer will choose the version that stands on its own.

In "coreIdea", state in at most 15 words of your own the general idea the candidate expresses. If you cannot state
any general idea, the candidate is probably not worth proposing.

Returning zero candidates is correct when the source contains nothing that could stand as a quotation.

Example.

SOURCE UNITS

[1] The old sailor had crossed the Atlantic eleven times.
[2] He rarely spoke of storms.
[3] What he spoke of instead was the calm: the long, windless days when nothing could be done.
[4] Those, he said, are what break a man; storms at least give him something to fight.

Response:

{"excerpts":[
{"startUnit":4,"endUnit":4,"references":[{"phrase":"Those","referentUnit":3},{"phrase":"he said","referentUnit":1}],"coreIdea":"Idle helplessness breaks people more than open struggle does.","reason":"Striking claim, but 'Those' refers to the calm days in unit 3."},
{"startUnit":3,"endUnit":4,"references":[{"phrase":"he","referentUnit":1}],"coreIdea":"Idle helplessness breaks people more than open struggle does.","reason":"Includes the referent of 'Those'; 'he' remains but the idea is clear."}
]}

Units 1 and 2 are biographical scene-setting and are not proposed.

Return only the structured response required by the response schema.
