import test from 'node:test'
import assert from 'node:assert/strict'
import {createHash,randomBytes} from 'node:crypto'
import {Sha256,hashBlob} from '../sha256.js'
test('SHA-256 matches native implementation across padding and chunk boundaries',()=>{for(const n of [0,1,3,55,56,63,64,65,127,128,129,65537,1050000]){const bytes=randomBytes(n),hash=new Sha256();for(let at=0;at<n;at+=37)hash.update(bytes.subarray(at,at+37));assert.equal(hash.hex(),createHash('sha256').update(bytes).digest('hex'),`length ${n}`)}})
test('hashes multi-chunk Blob and reports actual bytes',async()=>{const bytes=randomBytes(2200000),progress=[];assert.equal(await hashBlob(new Blob([bytes]),undefined,n=>progress.push(n)),createHash('sha256').update(bytes).digest('hex'));assert.equal(progress.at(-1),bytes.length);assert.equal(progress.length,3)})
test('cancelled hashing stops before sending data',async()=>{const controller=new AbortController();controller.abort();await assert.rejects(hashBlob(new Blob(['abc']),controller.signal),e=>e.name==='AbortError')})
